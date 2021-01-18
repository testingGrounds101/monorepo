package edu.nyu.classes.seats;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.*;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.event.cover.EventTrackingService;
import org.sakaiproject.event.cover.UsageSessionService;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SakaiGroupSyncTask {
    private static final Logger LOG = LoggerFactory.getLogger(SakaiGroupSyncTask.class);
    private static AtomicLong dbTimingThresholdMs = new AtomicLong(-1);

    private static class SakaiGroupSyncRequest {
        public enum Action {
            SYNC_SEAT_GROUP,
            DELETE_SAKAI_GROUP,
        }

        public final String id;
        public final Action action;
        public final String arg1;
        public final String arg2;

        public SakaiGroupSyncRequest(String id, Action action, String arg1, String arg2) {
            this.id = id;
            this.action = action;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }
    }

    // Bring the Sakai Groups we're managing into line with our Seat Groups
    public static void handleSakaiGroupSync() {
        long deadline = System.currentTimeMillis() + 5000;

        DB.transaction
            ("Synchronize Seat Groups with their Sakai Groups",
             (DBConnection db) -> {
                db.setTimingEnabled(dbTimingThresholdMs());
                try {

                    // Process a decent-size chunk, but with an upper limit.  We'll go around again soon anyway
                    List<SakaiGroupSyncRequest> requests =
                        db.run("select * from (select * from seat_sakai_group_sync_queue order by id) where rownum <= 500")
                        .executeQuery()
                        .map((row) -> new SakaiGroupSyncRequest(row.getString("id"),
                                                                SakaiGroupSyncRequest.Action.valueOf(row.getString("action")),
                                                                row.getString("arg1"),
                                                                row.getString("arg2")));

                    String lastProcessedId = null;
                    Set<String> alreadyProcessedArgs = new HashSet<>();
                    try {
                        for (SakaiGroupSyncRequest request : requests) {
                            // Right now it doesn't make sense to sync a seat group or delete a sakai group
                            // more than once, so skip over the dupes here.
                            if (alreadyProcessedArgs.contains(request.arg1)) {
                                lastProcessedId = request.id;
                                continue;
                            }

                            if (request.action == SakaiGroupSyncRequest.Action.SYNC_SEAT_GROUP) {
                                long startTime = System.currentTimeMillis();
                                Optional<String> siteId = db.run("select site_id from seat_group_section sgs " +
                                                                 " inner join seat_group sg on sg.section_id = sgs.id" +
                                                                 " where sg.id = ?")
                                    .param(request.arg1)
                                    .executeQuery()
                                    .oneString();

                                if (!siteId.isPresent()) {
                                    // Can't do much without a site.  Shouldn't normally happen...
                                    continue;
                                }

                                if (!Locks.trylockSiteForUpdate(siteId.get())) {
                                    // If we couldn't get a lock immediately, bail out and reprocess later.  This
                                    // should be a transient issue and we don't want to block.
                                    LOG.info(String.format("Couldn't lock site '%s'.  Aborting group sync for this run.",
                                                           siteId.get()));
                                    break;
                                }

                                try {
                                    SakaiGroupSync.syncSeatGroup(db, request.arg1);
                                    LOG.info(String.format("Synced seat group to sakai group in %dms", System.currentTimeMillis() - startTime));
                                    alreadyProcessedArgs.add(request.arg1);
                                } finally {
                                    Locks.unlockSiteForUpdate(siteId.get());
                                }
                            } else if (request.action == SakaiGroupSyncRequest.Action.DELETE_SAKAI_GROUP) {
                                Optional<String> siteId = db.run("select site_id from seat_group_section sgs where id = ?")
                                    .param(request.arg2)
                                    .executeQuery()
                                    .oneString();

                                if (!siteId.isPresent()) {
                                    // Can't do much without a site.  Shouldn't normally happen...
                                    continue;
                                }

                                if (!Locks.trylockSiteForUpdate(siteId.get())) {
                                    // If we couldn't get a lock immediately, bail out and reprocess later.  This
                                    // should be a transient issue and we don't want to block.
                                    LOG.info(String.format("Couldn't lock site '%s'.  Aborting group sync for this run.",
                                                           siteId.get()));
                                    break;
                                }

                                try {
                                    SakaiGroupSync.deleteSakaiGroup(db, request.arg1, request.arg2);
                                    alreadyProcessedArgs.add(request.arg1);
                                } finally {
                                    Locks.unlockSiteForUpdate(siteId.get());
                                }
                            } else {
                                LOG.error("Unknown action: " + request.action);
                            }

                            lastProcessedId = request.id;

                            if (System.currentTimeMillis() > deadline) {
                                // We've used up our allotted time.  Go around the main loop again to ensure
                                // we're not starving out seat group actions with slow site API changes.  Each
                                // group takes a few hundred ms.
                                break;
                            }
                        }
                    } finally {
                        if (lastProcessedId != null) {
                            // Mark requests as completed by deleting them.
                            for (SakaiGroupSyncRequest request : requests) {
                                if (request.id.compareTo(lastProcessedId) > 0) {
                                    break;
                                }

                                db.run("delete from seat_sakai_group_sync_queue where id = ?")
                                    .param(request.id)
                                    .executeUpdate();
                            }

                            db.commit();
                        }
                    }

                } catch (Exception e) {
                    LOG.error("Things have gone badly during handleSakaiGroupSyncTask: " + e);
                    e.printStackTrace();
                }

                return null;
            });
    }

    public static void login() {
        Session sakaiSession = SessionManager.getCurrentSession();
        sakaiSession.setUserId("admin");
        sakaiSession.setUserEid("admin");

        // establish the user's session
        UsageSessionService.startSession("admin", "127.0.0.1", "SakaiGroupSync");

        // update the user's externally provided realm definitions
        ((AuthzGroupService) ComponentManager.get("org.sakaiproject.authz.api.AuthzGroupService")).refreshUser("admin");

        // post the login event
        EventTrackingService.post(EventTrackingService.newEvent(UsageSessionService.EVENT_LOGIN, null, true));
    }

    public static void setDBTimingThresholdMs(long ms) {
        dbTimingThresholdMs.set(ms);
    }

    private static long dbTimingThresholdMs() {
        return dbTimingThresholdMs.get();
    }
}
