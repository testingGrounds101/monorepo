package edu.nyu.classes.jobs;

import org.quartz.CronScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.JobDetail;
import org.quartz.JobBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.JobKey;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.CronTrigger;
import org.quartz.SchedulerException;
import org.quartz.StatefulJob;

import org.sakaiproject.api.app.scheduler.JobBeanWrapper;
import org.sakaiproject.api.app.scheduler.SchedulerManager;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

import java.util.Set;
import java.util.stream.Collectors;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import java.util.HashSet;

// MySQL:
//
// create table TESOL_CONNECTION (connection_key varchar(96) primary key, date_added bigint, version bigint);
//
// Oracle:
//
// create table TESOL_CONNECTION (connection_key varchar2(96) primary key, date_added NUMBER, version NUMBER);
//

public class TesolFriendMaker {
    private final static int BATCH_SIZE = 128;

    private static final Logger LOG = LoggerFactory.getLogger(TesolFriendMaker.class);

    String JOB_NAME = "TesolFriendMaker";
    String JOB_GROUP = "TesolFriendMaker";

    public void init()
    {
        SchedulerManager schedulerManager = (SchedulerManager) ComponentManager.get("org.sakaiproject.api.app.scheduler.SchedulerManager");

        Scheduler scheduler = schedulerManager.getScheduler();

        try {
            if (!scheduler.isStarted()) {
                LOG.info("Doing nothing because the scheduler isn't started");
                return;
            }

            // Delete any old instances of the job
            scheduler.deleteJob(new JobKey(JOB_NAME, JOB_GROUP));

            // Then reschedule it
            String cronTrigger = ServerConfigurationService.getString("nyu.tesol-friend-maker.cron", "0 30 * * * ?");

            JobDetail detail = JobBuilder.newJob(TesolFriendMakerJob.class)
                    .withIdentity(JOB_NAME, JOB_GROUP)
                    .build();

            detail.getJobDataMap().put(JobBeanWrapper.SPRING_BEAN_NAME, this.getClass().toString());

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(JOB_NAME + "Trigger")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronTrigger))
                    .forJob(detail)
                    .build();


            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            LOG.error("Error while scheduling job", e);
        }
    }

    public void destroy()
    {
    }


    public static class TesolFriendMakerJob implements StatefulJob
    {
        private static final Logger LOG = LoggerFactory.getLogger(TesolFriendMakerJob.class);

        public void execute(JobExecutionContext context) {
            LOG.info("TesolFriendMaker running");

            Connection connection = null;

            boolean oldAutoCommit;

            try {
                connection = SqlService.borrowConnection();
                oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                List<String> siteIds = getTesolSites(connection);

                long version = System.currentTimeMillis();

                for (String siteId : siteIds) {
                    LOG.info("Creating connections for site: {}", siteId);
                    createConnectionsForSite(connection, siteId, version);
                }

                // Handle deletes...  Anything whose version is < version can be
                // deleted.  Remove the TESOL_CONNECTION entry and the
                // underlying friend link.
                handleDeletes(connection, version);

                connection.commit();

                connection.setAutoCommit(oldAutoCommit);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                if (connection != null) {
                    SqlService.returnConnection(connection);
                }
            }

            LOG.info("TesolFriendMaker finished");
        }

        private List<String> getTesolSites(Connection connection) throws SQLException {
            ResultSet rs = null;
            PreparedStatement query = null;
            List<String> result = new ArrayList<>();

            try {
                query = connection.prepareStatement("select site_id from sakai_site_property" +
                                                    " where name = 'tesol-course'");

                rs = query.executeQuery();

                while (rs.next()) {
                    result.add(rs.getString("site_id"));
                }
            } finally {
                if (rs != null) {
                    rs.close();
                }

                if (query != null) {
                    query.close();
                }
            }

            return result;
        }

        private void handleDeletes(Connection connection, long version) throws SQLException {
            // Any row whose version number wasn't touched by the update process
            // is now obsolete (i.e. student left a course).  Remove the
            // connection.
            PreparedStatement ps = connection.prepareStatement("select connection_key " +
                                                               " from TESOL_CONNECTION" +
                                                               " where version < ?");
            ps.setLong(1, version);

            ResultSet rs = ps.executeQuery();
            List<Pairing> pairings = new ArrayList<>();

            while (rs.next()) {
                String key = rs.getString("connection_key");
                Pairing p = Pairing.fromKey(key);
                pairings.add(p);
            }

            rs.close();

            for (Pairing p : pairings) {
                LOG.info("Deleting obsolete connection: {}", p.getKey());

                PreparedStatement delete_friend = connection.prepareStatement("delete from PROFILE_FRIENDS_T " +
                                                                              "where (user_uuid = ? AND friend_uuid = ?) OR (user_uuid = ? AND friend_uuid = ?)");
                delete_friend.setString(1, p.user1);
                delete_friend.setString(2, p.user2);
                delete_friend.setString(3, p.user2);
                delete_friend.setString(4, p.user1);
                delete_friend.executeUpdate();

                delete_friend.close();

                PreparedStatement delete_tesol = connection.prepareStatement("delete from TESOL_CONNECTION where connection_key = ?");
                delete_tesol.setString(1, p.getKey());
                delete_tesol.executeUpdate();

                delete_tesol.close();
            }
        }


        private void createConnectionsForSite(Connection connection, String siteId, long version) {
            try {
                Site site = SiteService.getSite(siteId);
                Set<Member> members = (Set<Member>) site.getMembers();

                List<String> userIds = members.stream().map(m -> m.getUserId()).collect(Collectors.toList());

                userIds = userIds.stream().filter(userId -> !SecurityService.isSuperUser(userId)).collect(Collectors.toList());

                List<Pairing> pairings = new ArrayList<>(BATCH_SIZE);

                for (int i = 0; i < userIds.size() - 1; i++) {
                    for (int j = i + 1; j < userIds.size(); j++) {
                        String user1 = userIds.get(i);
                        String user2 = userIds.get(j);

                        pairings.add(new Pairing(user1, user2));

                        if (pairings.size() == BATCH_SIZE) {
                            connectUsers(connection, pairings, version);
                            pairings.clear();
                        }
                    }
                }

                if (pairings.size() > 0) {
                    connectUsers(connection, pairings, version);
                    pairings.clear();
                }
            } catch (IdUnusedException e) {
                throw new RuntimeException(e);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        // Anyone who is already in PROFILE_FRIENDS_T but not TESOL_CONNECTION.  I.e. actual friends.
        private List<Pairing> findOrganicConnections(Connection connection, List<Pairing> pairings) {
            List<Pairing> result = new ArrayList<>();

            // select from profile_friends_t where user_uuid in (BIGLIST) AND friend_uuid in (BIGLIST)
            // Iterate...
            Set<String> allUsers = new HashSet<>();

            for (Pairing p : pairings) {
                allUsers.add(p.user1);
                allUsers.add(p.user2);
            }

            try {
                String placeholders = allUsers.stream().map(_p -> "?").collect(Collectors.joining(","));
                PreparedStatement ps = connection.prepareStatement("select user_uuid, friend_uuid from profile_friends_t" +
                                                                   " where (user_uuid in (" + placeholders + ")" +
                                                                   "   OR friend_uuid in (" + placeholders + "))");

                int pos = 0;
                for (int i = 0; i < 2; i++) {
                    for (String userId : allUsers) {
                        pos++;
                        ps.setString(pos, userId);
                    }
                }

                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String user = rs.getString("user_uuid");
                    String friend = rs.getString("friend_uuid");

                    Pairing p = new Pairing(user, friend);

                    if (pairings.indexOf(p) >= 0) {
                        result.add(p);
                    }
                }

                rs.close();
                ps.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return removeAlreadyConnected(connection, result);
        }

        // Remove any entries from `pairings` that already have an entry in `TESOL_CONNECTION`
        private List<Pairing> removeAlreadyConnected(Connection connection, List<Pairing> pairings) {
            if (pairings.isEmpty()) {
                return pairings;
            }

            // Anything already in TESOL_CONNECTION shouldn't be included here
            List<String> excludedKeys = new ArrayList<>();
            try {
                String placeholders = pairings.stream().map(_p -> "?").collect(Collectors.joining(","));
                PreparedStatement ps = connection.prepareStatement("select connection_key from TESOL_CONNECTION" +
                                                                   " where connection_key in (" + placeholders + ")");

                int pos = 0;
                for (Pairing p : pairings) {
                    pos++;
                    ps.setString(pos, p.getKey());
                }

                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    excludedKeys.add(rs.getString("connection_key"));
                }

                rs.close();
                ps.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return pairings.stream().filter(p -> excludedKeys.indexOf(p.getKey()) < 0).collect(Collectors.toList());
        }

        // Make friends!
        private void connectUsers(Connection connection, List<Pairing> pairings, long version) throws SQLException {
            List<Pairing> connectedOrganically = findOrganicConnections(connection, pairings);

            pairings.removeAll(connectedOrganically);

            List<Pairing> notYetConnected = removeAlreadyConnected(connection, pairings);

            PreparedStatement insert =
                    connection.prepareStatement("insert into TESOL_CONNECTION (connection_key, date_added, version)" +
                                                " values (?, ?, ?)");

            PreparedStatement insert_friend =
                    (SqlService.getVendor().equals("oracle") ?
                     connection.prepareStatement("insert into PROFILE_FRIENDS_T (id, user_uuid, friend_uuid, relationship, requested_date, confirmed_date, confirmed)" +
                                                 "values (PROFILE_FRIENDS_S.nextval, ?, ?, 1, current_timestamp, current_timestamp, 1)")
                     :
                     connection.prepareStatement("insert into PROFILE_FRIENDS_T (user_uuid, friend_uuid, relationship, requested_date, confirmed_date, confirmed)" +
                                                 "values (?, ?, 1, NOW(), NOW(), 1)"));


            for (Pairing p : notYetConnected) {
                LOG.info("Making a new friend: {}", p.getKey());

                insert.clearParameters();
                insert.setString(1, p.getKey());
                insert.setLong(2, System.currentTimeMillis());
                insert.setLong(3, version);
                insert.executeUpdate();

                insert_friend.clearParameters();
                insert_friend.setString(1, p.user1);
                insert_friend.setString(2, p.user2);
                insert_friend.executeUpdate();
            }

            // Update the version of any pairing that is still active.
            String placeholders = pairings.stream().map(_p -> "?").collect(Collectors.joining(","));

            PreparedStatement updateVersions =
                    connection.prepareStatement("update TESOL_CONNECTION set version = ? WHERE connection_key in (" + placeholders.toString() + ")");


            int pos = 1;
            updateVersions.setLong(pos, version);
            for (Pairing p : pairings) {
                pos++;
                updateVersions.setString(pos, p.getKey());
            }

            updateVersions.executeUpdate();
        }


        private static class Pairing {
            private String user1;
            private String user2;

            public Pairing(String user1, String user2) {
                this.user1 = user1;
                this.user2 = user2;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                } else if (other == null) {
                    return false;
                } else if (getClass() != other.getClass()) {
                    return false;
                } else {
                    return getKey().equals(((Pairing) other).getKey());
                }
            }

            @Override
            public int hashCode() {
                return getKey().hashCode();
            }

            public String getKey() {
                if (user1.compareTo(user2) <= 0) {
                    return user1 + "::" + user2;
                } else {
                    return user2 + "::" + user1;
                }
            }

            public static Pairing fromKey(String key) {
                String[] bits = key.split("::");

                return new Pairing(bits[0], bits[1]);
            }
        }
    }
}
