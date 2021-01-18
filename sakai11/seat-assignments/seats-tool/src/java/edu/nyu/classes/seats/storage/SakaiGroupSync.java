package edu.nyu.classes.seats.storage;

import edu.nyu.classes.seats.models.Member;
import edu.nyu.classes.seats.storage.db.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SakaiGroupSync {
    private static final Logger LOG = LoggerFactory.getLogger(SakaiGroupSync.class);
    private static final String IS_SEAT_GROUP_PROPERTY = "isSeatGroup";

    private static AtomicLong sequence = new AtomicLong(0);

    private static String buildId(DBConnection db) {
        return String.format("%013d_%016d_%s", System.currentTimeMillis(), sequence.incrementAndGet(), db.uuid());
    }

    public static void markGroupForSync(DBConnection db, String seatGroupId) throws SQLException {
        db.run("insert into seat_sakai_group_sync_queue (id, action, arg1) VALUES (?, ?, ?)")
            .param(buildId(db))
            .param("SYNC_SEAT_GROUP")
            .param(seatGroupId)
            .executeUpdate();
    }

    public static void markGroupForDelete(DBConnection db, String sakaiGroupId, String seatSectionId) throws SQLException {
        db.run("insert into seat_sakai_group_sync_queue (id, action, arg1, arg2) VALUES (?, ?, ?, ?)")
            .param(buildId(db))
            .param("DELETE_SAKAI_GROUP")
            .param(sakaiGroupId)
            .param(seatSectionId)
            .executeUpdate();
    }


    public static void syncSeatGroup(DBConnection db, String seatGroupId) throws Exception {
        int sectionSeatGroupCount = db.run("select count(1) from seat_group where section_id in " +
                                           " (select section_id from seat_group where id = ?)")
            .param(seatGroupId)
            .executeQuery()
            .getCount();

        if (sectionSeatGroupCount == 1) {
            // This is the only seat group in this section, so we don't create a
            // corresponding Sakai group for it.  It's just the section group.
            return;
        }

        if (syncSingleGroup(db, seatGroupId)) {
            // We've successfully synced the requested group.  Look for other groups that
            // haven't been synced yet and do those too.  This catches the case where we
            // originally just had one seat group that was never synced to a Sakai group,
            // but now that we have TWO seat groups it's a cohort, baby, and we're ready to
            // sync.
            //
            // Note: It's possible that these Group IDs are already in our queue to process
            // and we're just doubling up here, but that's OK.  We'd rather do redundant
            // work than miss updates.
            List<String> otherSeatGroupIds =
                db.run("select id from seat_group where sakai_group_id is null AND section_id in " +
                       " (select section_id from seat_group where id = ?)")
                .param(seatGroupId)
                .executeQuery()
                .getStringColumn("id");

            for (String otherSeatGroupId : otherSeatGroupIds) {
                syncSingleGroup(db, otherSeatGroupId);
            }
        }
    }

    private static boolean syncSingleGroup(DBConnection db, String seatGroupId) throws Exception {
        // Load our seat group members (keyed on sakai user_id)
        Map<String, Member> seatGroupMembers = new HashMap<>();

        db.run("select mem.netid, map.user_id, mem.role, mem.official" +
               " from seat_group sg" +
               " inner join seat_group_members mem on sg.id = mem.group_id" +
               " inner join sakai_user_id_map map on map.eid = mem.netid" +
               " where sg.id = ?")
            .param(seatGroupId)
            .executeQuery()
            .each((row) -> {
                    seatGroupMembers.put(row.getString("user_id"),
                                         new Member(row.getString("netid"),
                                                    row.getInt("official") == 1,
                                                    Member.Role.valueOf(row.getString("role")),
                                                    // unused here
                                                    Member.StudentLocation.IN_PERSON));
                });

        final AtomicBoolean success = new AtomicBoolean(false);

        db.run("select sec.site_id, sg.sakai_group_id, sg.name, sg.description, sec.id as section_id" +
               " from seat_group sg" +
               " inner join seat_group_section sec on sec.id = sg.section_id" +
               " where sg.id = ?")
            .param(seatGroupId)
            .executeQuery()
            .each((row) -> {
                    // Actually only expecting one row here...
                    String siteId = row.getString("site_id");
                    try {
                        Site site = SiteService.getSite(siteId);
                        Optional<Group> group = Optional.ofNullable(row.getString("sakai_group_id"))
                            .map((groupId) -> site.getGroup(groupId));

                        String groupTitle = String.format("Cohort: %s-%s",
                                SeatsStorage.buildSectionShortName(db, row.getString("section_id")),
                                row.getString("name"));

                        String groupDescription = row.getString("description");
                        if (groupDescription == null) {
                            groupDescription = "";
                        }

                        if (!group.isPresent()) {
                            // Either sg.sakai_group_id was null, or the groupId is bogus.  This might
                            // happen if a section is moved between sites, or if an instructor manually
                            // deleted our group.  Either way, you're getting a new group.
                            group = createSakaiGroup(db, site, seatGroupId, groupTitle, groupDescription);
                        }

                        if (!group.isPresent())  {
                            // We tried and failed to create the group.  Bailing out.
                            return;
                        }

                        group.get().setTitle(groupTitle);
                        group.get().setDescription(groupDescription);
                        applyMemberUpdates(group.get(), seatGroupMembers);
                        SiteService.save(site);

                        success.set(true);
                    } catch (IdUnusedException e) {
                        LOG.error("site not found: " + siteId);
                    } catch (PermissionException e) {
                        LOG.error("Permission denied updating site: " + siteId);
                    }
                });

                return success.get();
    }

    private static void applyMemberUpdates(Group sakaiGroup, Map<String, Member> seatGroupMembers) {
        // Temporarily unlock the group for our own nefarious purposes
        String lock = sakaiGroup.getProperties().getProperty(Group.GROUP_PROP_LOCKED_BY);
        sakaiGroup.unlockGroup();

        sakaiGroup.removeMembers();
        for (String userId : seatGroupMembers.keySet()) {
            Member m = seatGroupMembers.get(userId);
            sakaiGroup.insertMember(userId, m.sakaiRoleId(), true, false);
        }

        sakaiGroup.lockGroup(lock);
    }

    private static Optional<Group> createSakaiGroup(DBConnection db, Site site, String seatGroupId, String groupTitle, String groupDescription) {
        try {
            Group newGroup = site.addGroup();
            newGroup.getProperties().addProperty(IS_SEAT_GROUP_PROPERTY, "true");
            newGroup.getProperties().addProperty(Group.GROUP_PROP_WSETUP_CREATED, "true");
            newGroup.getProperties().addProperty(Group.GROUP_PROP_LOCKED_BY, "nyu.seat-assignments");

            newGroup.setTitle(groupTitle);
            newGroup.setDescription(groupDescription);

            if (db.run("update seat_group set sakai_group_id = ? where id = ?")
                .param(newGroup.getId())
                .param(seatGroupId)
                .executeUpdate() > 0) {

                return Optional.of(newGroup);
            }
        } catch (Exception e) {
            LOG.error(String.format("Failure while creating new group for site '%s', seat group: '%s': %s",
                                    site.getId(), seatGroupId, e));
            e.printStackTrace();
        }

        // Failcake
        return Optional.empty();
    }

    public static void deleteSakaiGroup(DBConnection db, String sakaiGroupId, String seatSectionId) {
        try {
            if (deleteSingleSakaiGroup(db, sakaiGroupId)) {
                // If there is only one seat group left, remove its sakai group too
                List<String> remainingSakaiGroups = db.run("select sakai_group_id from seat_group where section_id = ?")
                    .param(seatSectionId)
                    .executeQuery()
                    .getStringColumn("sakai_group_id");

                if ((remainingSakaiGroups.size() == 1) && remainingSakaiGroups.get(0) != null) {
                    if (deleteSingleSakaiGroup(db, remainingSakaiGroups.get(0))) {
                        if (db.run("update seat_group set sakai_group_id = null where section_id = ? AND sakai_group_id = ?")
                            .param(seatSectionId)
                            .param(remainingSakaiGroups.get(0))
                            .executeUpdate() != 1) {
                            throw new RuntimeException("Unexpectedly matched multiple sakai groups");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(String.format("Failure ignored while trying to delete group '%s': %s",
                                    sakaiGroupId, e));
            e.printStackTrace();
        }
    }

    public static boolean deleteSingleSakaiGroup(DBConnection db, String sakaiGroupId) throws Exception {
        Optional<String> siteId = db.run("select site_id from sakai_site_group where group_id = ?")
            .param(sakaiGroupId)
            .executeQuery()
            .oneString();

        if (!siteId.isPresent()) {
            return false;
        }

        Site site = SiteService.getSite(siteId.get());
        Group group = site.getGroup(sakaiGroupId);

        if (group == null) {
            LOG.error("No group found for ID: " + sakaiGroupId);
            return false;
        }

        if ("true".equals(group.getProperties().getProperty(IS_SEAT_GROUP_PROPERTY))) {
            group.unlockGroup();
            site.removeGroup(group);
            SiteService.save(site);

            return true;
        } else {
            LOG.error("Refusing to delete a non-seat-group group");
            return false;
        }
    }

    public static void markSectionForSync(DBConnection db, String sectionId) throws SQLException {
        db.run("select id from seat_group where section_id = ?")
            .param(sectionId)
            .executeQuery()
            .each((row) -> {
                markGroupForSync(db, row.getString("id"));
            });
    }
}
