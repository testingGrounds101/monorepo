package edu.nyu.classes.seats.storage;

import edu.nyu.classes.seats.storage.db.*;

import java.util.*;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Locks {

    private static final Logger LOG = LoggerFactory.getLogger(Locks.class);

    // If you've held a lock this long, something is wrong and we'll forcibly unlock
    // you.
    private static int MAX_LOCK_AGE_MS = 5000;

    private static ThreadLocal<HashSet<String>> locksHeldBySiteId = ThreadLocal.withInitial(() -> new HashSet<>());

    public static boolean trylockSiteForUpdate(String siteId) {
        if (locksHeldBySiteId.get().contains(siteId)) {
            // Lock already held
            return true;
        }

        return DB.transaction("Get a lock for site: " + siteId,
                              (db) -> {
                                  long now = System.currentTimeMillis();
                                  db.run("delete from seat_sync_locks where site_id = ? AND lock_time < ?")
                                      .param(siteId)
                                      .param(now - MAX_LOCK_AGE_MS)
                                      .executeUpdate();

                                  try {
                                      db.run("insert into seat_sync_locks (site_id, lock_time) values (?, ?)")
                                          .param(siteId)
                                          .param(now)
                                          .executeUpdate();
                                  } catch (SQLException e) {
                                      if (db.isConstraintViolation(e)) {
                                          db.rollback();

                                          return false;
                                      } else {
                                          throw e;
                                      }
                                  }

                                  db.commit();
                                  locksHeldBySiteId.get().add(siteId);

                                  if (locksHeldBySiteId.get().size() > 1) {
                                      // Did someone forget to release a lock?
                                      LOG.warn("WARNING: Multiple locks held by a single thread.  This shouldn't happen!");

                                  }

                                  return true;
                              });
    }

    public static void lockSiteForUpdate(String siteId) {
        // This should terminate when the lock succeeds...
        for (int i = 0; i < 10; i++) {
            if (trylockSiteForUpdate(siteId)) {
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void unlockSiteForUpdate(String siteId) {
        DB.transaction("Unlock site: " + siteId,
                       (db) -> {
                           db.run("delete from seat_sync_locks where site_id = ?").param(siteId).executeUpdate();
                           db.commit();

                           locksHeldBySiteId.get().remove(siteId);
                           return null;
                       });
    }

}
