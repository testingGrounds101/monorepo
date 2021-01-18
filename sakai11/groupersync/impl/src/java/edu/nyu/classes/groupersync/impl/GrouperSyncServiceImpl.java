package edu.nyu.classes.groupersync.impl;

import edu.nyu.classes.groupersync.api.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.db.cover.SqlService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.sakaiproject.telemetry.cover.Telemetry;

public class GrouperSyncServiceImpl implements GrouperSyncService {

    private static final Log log = LogFactory.getLog(GrouperSyncServiceImpl.class);
    private static final String SYNCED_STATUS = "synced";

    // Return information about a Sakai group that was marked as needing syncing.
    //
    // Returns null if the group isn't marked for sync at all.
    @Override
    public GroupInfo getGroupInfo(final String sakaiGroupId) throws GrouperSyncException {
        final GroupInfo[] result = new GroupInfo[1];

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement("select ggd.group_id, ggd.sakai_group_id, ggd.description, gss.status, gss.update_mtime" +
                            " from grouper_group_definitions ggd" +
                            " inner join grouper_sync_status gss on gss.group_id = ggd.group_id" +
                            " where ggd.sakai_group_id = ? AND ggd.deleted != 1");
                    ps.setString(1, sakaiGroupId);

                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        Date updateTime = rs.getTimestamp("update_mtime", Calendar.getInstance());
                        long updateTimeMs = 0;

                        if (updateTime != null) {
                            updateTimeMs = updateTime.getTime();
                        }

                        result[0] = new GroupInfo(rs.getString("description"),
                                rs.getString("group_id"),
                                rs.getString("sakai_group_id"),
                                SYNCED_STATUS.equals(rs.getString("status")),
                                updateTimeMs);
                    }

                    rs.close();
                    ps.close();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure when finding group ID for Sakai group: " + sakaiGroupId, e);
        }

        return result[0];
    }

    @Override
    public Set<UserWithRole> getMembers(final String groupId) throws GrouperSyncException {
        final Set<UserWithRole> result = new HashSet<UserWithRole>();

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement("select netid, role from grouper_group_users where group_id = ?");
                    ps.setString(1, groupId);

                    ResultSet rs = ps.executeQuery();

                    while (rs.next()) {
                        result.add(new UserWithRole(rs.getString("netid"), rs.getString("role")));
                    }

                    rs.close();
                    ps.close();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure when fetching members for group: " + groupId, e);
        }

        return result;
    }

    @Override
    public void recordChanges(final String groupId,
                              final Set<UserWithRole> addedUsers,
                              final Set<UserWithRole> droppedUsers,
                              final Set<UserWithRole> changedRoles)
            throws GrouperSyncException {
        try {
            if (addedUsers.isEmpty() && droppedUsers.isEmpty() && changedRoles.isEmpty()) {
                // No change needed.
                return;
            }

            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;

                    // Drop users that were removed from groups or who had their roles changed
                    ps = connection.prepareStatement("delete from grouper_group_users where group_id = ? AND netid = ?");

                    for (UserWithRole dropped : Sets.union(droppedUsers, changedRoles)) {
                        ps.setString(1, groupId);
                        ps.setString(2, dropped.getUsername());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                    ps.close();

                    // Handle new users and users with changed roles
                    ps = connection.prepareStatement("insert into grouper_group_users (group_id, netid, role) values (?, ?, ?)");

                    for (UserWithRole added : Sets.union(addedUsers, changedRoles)) {
                        ps.setString(1, groupId);
                        ps.setString(2, added.getUsername());
                        ps.setString(3, added.getRole());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                    ps.close();

                    // Update the "ready for sync" mtime so groupsync will pick up our changes
                    ps = connection.prepareStatement("update grouper_group_definitions set ready_for_sync_time = ? WHERE group_id = ?");
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, groupId);
                    ps.executeUpdate();
                    ps.close();

                    connection.commit();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure when recording change in members for group: " + groupId, e);
        }
    }

    @Override
    public Date getLastRunDate() throws GrouperSyncException {
        // Default to 30 days ago just to avoid checking *every* site...
        final Date[] result = new Date[]{new Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))};

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement("select value from grouper_status where setting = 'last_run_time'");

                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        result[0] = new Date(Long.valueOf(rs.getString(1)));
                    }

                    rs.close();
                    ps.close();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure when getting job last_run_time", e);
        }

        return result[0];
    }

    @Override
    public void setLastRunDate(final Date date) throws GrouperSyncException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement("delete from grouper_status where setting = 'last_run_time'");
                    ps.executeUpdate();
                    ps.close();

                    ps = connection.prepareStatement("insert into grouper_status (setting, value) values (?, ?)");
                    ps.setString(1, "last_run_time");
                    ps.setString(2, String.valueOf(date.getTime()));
                    ps.executeUpdate();
                    ps.close();

                    Telemetry.TelemetryTimer recentTimer = Telemetry.startTimer("recent_commit_time");
                    Telemetry.TelemetryTimer timer = Telemetry.startTimer("commit_time");
                    connection.commit();
                    Telemetry.finishTimer(timer);
                    Telemetry.finishTimer(recentTimer);
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure when setting job last_run_time", e);
        }
    }

    @Override
    public void markGroupForSync(final String groupId, final String grouperGroupId, final String sakaiGroupId, final String description) throws GrouperSyncException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement insert = connection.prepareStatement("insert into grouper_group_definitions (group_id, grouper_group_id, sakai_group_id, description, mtime, ready_for_sync_time) values (?, ?, ?, ?, ?, ?)");

                    insert.setString(1, groupId);
                    insert.setString(2, grouperGroupId);
                    insert.setString(3, sakaiGroupId);
                    insert.setString(4, description);
                    insert.setLong(5, System.currentTimeMillis());
                    insert.setLong(6, System.currentTimeMillis());

                    insert.executeUpdate();
                    insert.close();

                    insert = connection.prepareStatement("insert into grouper_sync_status (group_id, grouper_group_id, status) values (?, ?, ?)");

                    insert.setString(1, groupId);
                    insert.setString(2, grouperGroupId);
                    insert.setString(3, "new");

                    insert.executeUpdate();
                    insert.close();

                    connection.commit();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure while inserting group", e);
        }
    }


    @Override
    public void updateDescription(final String groupId, final String description) throws GrouperSyncException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement insert = connection.prepareStatement("update grouper_group_definitions set description = ?, mtime = ?, ready_for_sync_time = ? where group_id = ?");

                    long now = System.currentTimeMillis();

                    insert.setString(1, description);
                    insert.setLong(2, now);
                    insert.setLong(3, now);
                    insert.setString(4, groupId);

                    insert.executeUpdate();
                    insert.close();

                    connection.commit();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure while updating description", e);
        }
    }


    @Override
    public boolean isGroupAvailable(final String groupId) throws GrouperSyncException {
        final boolean[] result = new boolean[1];

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement("select count(1) from grouper_group_definitions where group_id = ?");
                    ps.setString(1, groupId);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        result[0] = (rs.getInt(1) == 0);
                    }

                    rs.close();
                    ps.close();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure while checking group availability", e);
        }

        return result[0];
    }


    @Override
    public void deleteGroup(final String groupId) throws GrouperSyncException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    long now = System.currentTimeMillis();

                    // Mark as deleted
                    //
                    // Note: force ready_for_sync_time here because we can't
                    // rely on the Quartz job to touch this once it's marked as
                    // deleted.
                    PreparedStatement insert = connection.prepareStatement("update grouper_group_definitions set deleted = 1, mtime = ?, ready_for_sync_time = ? where group_id = ?");
                    insert.setLong(1, now);
                    insert.setLong(2, now);
                    insert.setString(3, groupId);
                    insert.executeUpdate();
                    insert.close();

                    // And drop all members
                    PreparedStatement dropMembers = connection.prepareStatement("delete from grouper_group_users where group_id = ?");
                    dropMembers.setString(1, groupId);
                    dropMembers.executeUpdate();
                    dropMembers.close();

                    // Clear the sync status
                    PreparedStatement clearStatus = connection.prepareStatement("delete from grouper_sync_status where group_id = ?");
                    clearStatus.setString(1, groupId);
                    clearStatus.executeUpdate();
                    clearStatus.close();

                    connection.commit();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure while deleting group", e);
        }
    }


    // Delete any group from Grouper that was deleted in Sakai (e.g. adhoc group removed, section detached)
    @Override
    public void deleteDetachedGroups() throws GrouperSyncException {
        // Weird inner select to allow support for MySQL which can't update the
        // table it's sub-selecting from.
        final String detachedGroupSql = ("select ggd.group_id" +
                " from (select group_id, sakai_group_id, deleted from grouper_group_definitions) ggd" +
                " left outer join sakai_site_group ssg on ssg.group_id = ggd.sakai_group_id" +
                " left outer join sakai_site ss on ss.site_id = ggd.sakai_group_id" +
                " where ggd.deleted != 1 AND ssg.group_id is NULL AND ss.site_id is NULL");

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    // Drop all members
                    PreparedStatement dropMembers = connection.prepareStatement("delete from grouper_group_users where group_id in (" + detachedGroupSql + ")");
                    dropMembers.executeUpdate();
                    dropMembers.close();

                    // Clear the sync status
                    PreparedStatement clearStatus = connection.prepareStatement("delete from grouper_sync_status where group_id in (" + detachedGroupSql + ")");
                    clearStatus.executeUpdate();
                    clearStatus.close();

                    // Mark as deleted
                    long now = System.currentTimeMillis();

                    PreparedStatement insert = connection.prepareStatement("update grouper_group_definitions set deleted = 1, mtime = ?, ready_for_sync_time = ?" +
                            " where group_id in (" + detachedGroupSql + ")");
                    insert.setLong(1, now);
                    insert.setLong(2, now);
                    insert.executeUpdate();
                    insert.close();

                    connection.commit();
                }
            });
        } catch (SQLException e) {
            throw new GrouperSyncException("Failure while deleting detached groups", e);
        }
    }


    interface DBAction {
        void execute(Connection conn) throws SQLException;
    }

    static class DB {

        public static void connection(DBAction action) throws SQLException {
            Connection connection = null;
            boolean oldAutoCommit;

            try {
                connection = SqlService.borrowConnection();
                oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                action.execute(connection);

                connection.setAutoCommit(oldAutoCommit);
            } finally {
                if (connection != null) {
                    SqlService.returnConnection(connection);
                }
            }
        }
    }


    @Override
    public void init() {
    }

    @Override
    public void destroy() {
    }

}
