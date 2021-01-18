package org.sakaiproject.scormcloudservice.impl;

import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.scormcloudservice.api.ScormException;
import org.sakaiproject.scormcloudservice.api.ScormUploadStatus;
import org.sakaiproject.component.cover.ServerConfigurationService;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


/*
 * Manage the SCORM uploads through their processing stages.
 */
public class ScormServiceStore {

    final int MAX_ERROR_MESSAGE = 1024;
    final int RETRY_COUNT = ServerConfigurationService.getInt("scormcloudservice.max-upload-retries", 10);
    final int RETRY_DELAY_MS = ServerConfigurationService.getInt("scormcloudservice.upload-retry-delay-ms", 60000);

    // When we record the time we last ran a sync job, we subtract this many
    // milliseconds from what we store.  That way, we're a little more robust
    // against clock drift and slow-committing transactions.  This comes at the
    // cost of sometimes syncing the same course twice in a row, but it
    // shouldn't matter anyway.
    final long SYNC_OFFSET_MS = 60000;

    enum JOB_STATUS {
        NEW,
        PROCESSING,
        COMPLETED,
        TEMPORARILY_FAILED,
        PERMANENTLY_FAILED
    }


    interface DBAction {
        public void execute(Connection conn) throws SQLException;
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


    public void addCourse(final String siteId, final String externalId, final String resourceId, final String title, final boolean graded) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ps = connection.prepareStatement("insert into scs_scorm_job (uuid, siteid, externalid, resourceid, title, graded, ctime, mtime, retry_count, status, deleted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        ps.setString(1, mintId());
                        ps.setString(2, siteId);
                        ps.setString(3, externalId);
                        ps.setString(4, resourceId);
                        ps.setString(5, title);
                        ps.setInt(6, graded ? 1 : 0);
                        ps.setLong(7, System.currentTimeMillis());
                        ps.setLong(8, System.currentTimeMillis());
                        ps.setInt(9, 0);
                        ps.setString(10, JOB_STATUS.NEW.toString());
                        ps.setInt(11, 0);

                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when adding job to store", e);
        }
    }


    public void updateCourse(final String siteId, final String externalId, final String title, final boolean graded) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;

                    try {
                        ps = connection.prepareStatement("update scs_scorm_job set title = ?, graded = ? WHERE siteid = ? AND externalid = ?");
                        ps.setString(1, title);
                        ps.setInt(2, graded ? 1 : 0);
                        ps.setString(3, siteId);
                        ps.setString(4, externalId);
                        ps.executeUpdate();

                        // This might update nothing if the course hasn't been imported yet, but that's fine.
                        ps = connection.prepareStatement("update scs_scorm_course set title = ?, graded = ? WHERE siteid = ? AND externalid = ?");
                        ps.setString(1, title);
                        ps.setInt(2, graded ? 1 : 0);
                        ps.setString(3, siteId);
                        ps.setString(4, externalId);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when updating job", e);
        }
    }


    public List<ScormJob> getPendingJobs() throws ScormException {
        // check for things that are NEW, or failed + haven't hit their retry count + are candidates for being retried.
        final List<ScormJob> result = new ArrayList<ScormJob>();

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = connection.prepareStatement("select * from scs_scorm_job where status = ? OR (status = ? AND mtime <= ?)");
                        ps.setString(1, JOB_STATUS.NEW.toString());
                        ps.setString(2, JOB_STATUS.TEMPORARILY_FAILED.toString());
                        ps.setLong(3, System.currentTimeMillis() - RETRY_DELAY_MS);

                        rs = ps.executeQuery();

                        while (rs.next()) {
                            ScormJob job = new ScormJob(rs.getString("uuid"), rs.getString("siteid"),
                                    rs.getString("externalid"), rs.getString("resourceid"),
                                    rs.getString("title"), (rs.getInt("graded") == 1), (rs.getInt("deleted") == 1));
                            result.add(job);
                        }
                    } finally {
                        if (ps != null) {
                            ps.close();
                        }
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when changing job status", e);
        }

        return result;
    }


    public void startProcessing(final ScormJob job) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    try {
                        ps = connection.prepareStatement("update scs_scorm_job set mtime = ?, status = ? WHERE uuid = ?");
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, JOB_STATUS.PROCESSING.toString());
                        ps.setString(3, job.getId());

                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when changing job status", e);
        }
    }


    public void recordFailure(final ScormJob job, final String errorMessage) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    try {
                        // Update the job
                        ps = connection.prepareStatement("update scs_scorm_job set mtime = ?, status = ?, error_message = ?, retry_count = retry_count + 1 WHERE uuid = ?");
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, JOB_STATUS.TEMPORARILY_FAILED.toString());
                        ps.setString(3, (errorMessage.length() > MAX_ERROR_MESSAGE) ? errorMessage.substring(0, MAX_ERROR_MESSAGE) : errorMessage);
                        ps.setString(4, job.getId());
                        ps.executeUpdate();

                        // Mark jobs as permanently failed as needed
                        ps = connection.prepareStatement("update scs_scorm_job set mtime = ?, status = ? WHERE retry_count >= ? AND status != ?");
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, JOB_STATUS.PERMANENTLY_FAILED.toString());
                        ps.setInt(3, RETRY_COUNT);
                        ps.setString(4, JOB_STATUS.PERMANENTLY_FAILED.toString());
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when changing job status", e);
        }
    }


    public void markCompleted(final ScormJob job) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    try {
                        // Update the job
                        ps = connection.prepareStatement("update scs_scorm_job set mtime = ?, status = ? WHERE uuid = ?");
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, JOB_STATUS.COMPLETED.toString());
                        ps.setString(3, job.getId());
                        ps.executeUpdate();

                        // Create a new course for the job
                        ps = connection.prepareStatement("insert into scs_scorm_course (uuid, siteid, externalid, resourceid, title, graded, ctime, mtime, deleted) values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                        ps.setString(1, job.getId());
                        ps.setString(2, job.getSiteId());
                        ps.setString(3, job.getExternalId());
                        ps.setString(4, job.getResourceId());
                        ps.setString(5, job.getTitle());
                        ps.setInt(6, job.getGraded() ? 1 : 0);
                        ps.setLong(7, System.currentTimeMillis());
                        ps.setLong(8, System.currentTimeMillis());
                        ps.setInt(9, job.isDeleted() ? 1 : 0);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();

                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when changing job status", e);
        }
    }


    public String findCourseId(final String siteId, final String externalId) throws ScormException {
        final String[] result = {null};

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        // Find the ID for the course
                        ps = connection.prepareStatement("select uuid from scs_scorm_course where siteid = ? AND externalid = ?");
                        ps.setString(1, siteId);
                        ps.setString(2, externalId);
                        rs = ps.executeQuery();

                        if (rs.next()) {
                            result[0] = rs.getString("uuid");
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });

            return result[0];
        } catch (Exception e) {
            throw new ScormException("Failure when searching for course", e);
        }
    }


    public String findCourseOrJobId(final String siteId, final String externalId) throws ScormException {
        String id = findCourseId(siteId, externalId);

        if (id != null) {
            return id;
        }

        final String[] result = {null};

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        // Find the ID for the course
                        ps = connection.prepareStatement("select uuid from scs_scorm_job where siteid = ? AND externalid = ?");
                        ps.setString(1, siteId);
                        ps.setString(2, externalId);
                        rs = ps.executeQuery();

                        if (rs.next()) {
                            result[0] = rs.getString("uuid");
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });

            return result[0];
        } catch (Exception e) {
            throw new ScormException("Failure when searching for course/job", e);
        }
    }

    public String hasRegistration(final String siteId, final String externalId, final String userId) throws ScormException {
        final String[] result = {null};
        final String courseId = findCourseId(siteId, externalId);

        if (courseId == null) {
            throw new ScormException("Couldn't find SCORM course");
        }

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = connection.prepareStatement("select uuid from scs_scorm_registration where courseid = ? AND userid = ?");
                        ps.setString(1, courseId);
                        ps.setString(2, userId);

                        rs = ps.executeQuery();
                        if (rs.next()) {
                            result[0] = rs.getString("uuid");
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });

            return result[0];
        } catch (SQLException e) {
            throw new ScormException("Unknown registration status", e);
        }
    }


    public void recordRegistration(final String registrationId, final String courseId, final String userId)
            throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    try {
                        ps = connection.prepareStatement("insert into scs_scorm_registration (uuid, courseid, userid, ctime, mtime) values (?, ?, ?, ?, ?)");
                        ps.setString(1, registrationId);
                        ps.setString(2, courseId);
                        ps.setString(3, userId);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.setLong(5, System.currentTimeMillis());

                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failed to record registration", e);
        }
    }

    public String mintId() {
        return IdManager.getInstance().createUuid();
    }


    public void markCourseForGradeSync(final String siteId, final String externalId) throws ScormException {
        try {
            final String courseId = findCourseId(siteId, externalId);

            if (courseId == null) {
                return;
            }

            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    try {
                        // Update the job
                        ps = connection.prepareStatement("update scs_scorm_course set mtime = ? where uuid = ?");
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, courseId);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when marking course for grade sync", e);
        }
    }


    public List<String> getCoursesNeedingSync() throws ScormException {
        final List<String> courseIds = new ArrayList<String>();

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;

                    try {
                        ps = connection.prepareStatement("select last_run_time from scs_scorm_job_info where jobname = 'GradeSync'");
                        rs = ps.executeQuery();

                        long lastSyncTime = 0;

                        if (rs.next()) {
                            lastSyncTime = rs.getLong("last_run_time");
                        }


                        // Originally we thought it might be worth excluding
                        // courses that we already had a complete set of scores
                        // for, but now I'm not sure.  It looks like grades can
                        // be reset from the SCORM side, we might need to resync
                        // courses just in case their grades have changed.
                        //
                        // ps = connection.prepareStatement("select uuid from
                        // scs_scorm_course where mtime >= ? AND uuid in (select
                        // distinct reg.courseid from scs_scorm_registration reg
                        // left outer join scs_scorm_scores scores on reg.uuid =
                        // scores.registrationid where scores.registrationid is
                        // null)");

                        ps = connection.prepareStatement("select uuid from scs_scorm_course where mtime >= ?");
                        ps.setLong(1, lastSyncTime);

                        rs = ps.executeQuery();
                        while (rs.next()) {
                            courseIds.add(rs.getString("uuid"));
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Unknown registration status", e);
        }

        return courseIds;
    }


    public Date getLastSyncTime() throws ScormException {
        final long[] result = { 0l };

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;

                    try {
                        ps = connection.prepareStatement("select last_run_time from scs_scorm_job_info where jobname = 'GradeSync'");
                        rs = ps.executeQuery();

                        if (rs.next()) {
                            result[0] = rs.getLong("last_run_time");
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }

                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Couldn't update sync time", e);
        }

        return new Date(result[0]);
    }

    public void setLastSyncTime(final Date newSyncTime) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;

                    long offsetTime = newSyncTime.getTime() - SYNC_OFFSET_MS;

                    try {
                        ps = connection.prepareStatement("insert into scs_scorm_job_info (jobname, last_run_time) values ('GradeSync', ?)");
                        ps.setLong(1, offsetTime);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        ps = connection.prepareStatement("update scs_scorm_job_info set last_run_time = ? where jobname = 'GradeSync'");
                        ps.setLong(1, offsetTime);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Couldn't update sync time", e);
        }
    }


    private void checkBatchSuccess(int[] jdbcReturnCodes) throws SQLException {
        for (int code : jdbcReturnCodes) {
            if (code == Statement.EXECUTE_FAILED) {
                throw new SQLException("Failure executing batch.  Code was: " + code);
            }
        }
    }

    public void recordScores(final Map<String, Double> userToScore) throws ScormException {
        try {
            if (userToScore.isEmpty()) {
                return;
            }

            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement deletes = connection.prepareStatement("delete from scs_scorm_scores where registrationid = ?");
                    PreparedStatement inserts = connection.prepareStatement("insert into scs_scorm_scores (registrationid, score) values (?, ?)");

                    try {
                        for (String registrationId : userToScore.keySet()) {
                            Double score = userToScore.get(registrationId);

                            deletes.setString(1, registrationId);
                            deletes.addBatch();

                            inserts.setString(1, registrationId);
                            inserts.setDouble(2, score);
                            inserts.addBatch();
                        }

                        checkBatchSuccess(deletes.executeBatch());
                        checkBatchSuccess(inserts.executeBatch());
                        connection.commit();
                    } finally {
                        deletes.close();
                        inserts.close();
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Couldn't record score for registrations", e);
        }
    }


    public void removeScore(final String registrationId) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;

                    try {
                        ps = connection.prepareStatement("delete from scs_scorm_scores where registrationid = ?");
                        ps.setString(1, registrationId);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Couldn't remove score for registration: " + registrationId, e);
        }
    }


    public ScormCourse getCourseForRegistration(final String registrationId) throws ScormException {
        final ScormCourse[] result = new ScormCourse[1];

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = connection.prepareStatement("select course.* from scs_scorm_course course inner join scs_scorm_registration reg on reg.courseid = course.uuid where reg.uuid = ?");
                        ps.setString(1, registrationId);

                        rs = ps.executeQuery();

                        while (rs.next()) {
                            ScormCourse course = new ScormCourse(rs.getString("uuid"), rs.getString("siteid"),
                                    rs.getString("externalid"), rs.getString("resourceid"),
                                    rs.getString("title"), (rs.getInt("graded") == 1), (rs.getInt("deleted") == 1));
                            result[0] = course;
                        }
                    } finally {
                        if (ps != null) {
                            ps.close();
                        }
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure finding course for registration: " + registrationId, e);
        }

        return result[0];
    }


    public void markAsDeleted(final String courseId) throws ScormException {
        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;

                    try {
                        ps = connection.prepareStatement("update scs_scorm_job set deleted = 1 WHERE uuid = ?");
                        ps.setString(1, courseId);
                        ps.executeUpdate();

                        // This might update nothing if the course hasn't been imported yet, but that's fine.
                        ps = connection.prepareStatement("update scs_scorm_course set deleted = 1 WHERE uuid = ?");
                        ps.setString(1, courseId);
                        ps.executeUpdate();
                    } finally {
                        connection.commit();
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure when marking job as deleted: " + courseId, e);
        }
    }


    public ScormCourse getCourseForId(final String courseId) throws ScormException {
        final ScormCourse[] result = new ScormCourse[1];

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = connection.prepareStatement("select * from scs_scorm_course where uuid = ?");
                        ps.setString(1, courseId);

                        rs = ps.executeQuery();

                        if (rs.next()) {
                            ScormCourse course = new ScormCourse(rs.getString("uuid"), rs.getString("siteid"),
                                    rs.getString("externalid"), rs.getString("resourceid"),
                                    rs.getString("title"), (rs.getInt("graded") == 1), (rs.getInt("deleted") == 1));
                            result[0] = course;
                        }
                    } finally {
                        if (ps != null) {
                            ps.close();
                        }
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure finding course for ID: " + courseId, e);
        }

        return result[0];
    }


    public String getUserForRegistration(final String registrationId) throws ScormException {
        final String[] result = new String[1];

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = connection.prepareStatement("select * from scs_scorm_registration where uuid = ?");
                        ps.setString(1, registrationId);

                        rs = ps.executeQuery();

                        if (rs.next()) {
                            result[0] = rs.getString("userId");
                        }
                    } finally {
                        if (ps != null) {
                            ps.close();
                        }
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure finding course for registration: " + registrationId, e);
        }

        return result[0];
    }

    public Map<String, String> getUsersForRegistrations(final Collection<String> registrationIdsCollection) throws ScormException {
        final Map<String, String> result = new HashMap<>(registrationIdsCollection.size());
        List<String> registrationIds = new ArrayList<>(registrationIdsCollection);

        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    int pageSize = 500;
                    for (int start = 0; start < registrationIds.size(); start += pageSize) {
                        int end = Math.min(registrationIds.size(), start + pageSize);

                        List<String> subList = registrationIds.subList(start, end);
                        String placeholders = subList.stream().map(s -> "?").collect(Collectors.joining(","));

                        PreparedStatement ps = null;
                        ResultSet rs = null;
                        try {
                            ps = connection.prepareStatement(String.format("select userId, uuid from scs_scorm_registration where uuid in (%s)", placeholders));

                            for (int i = 0; i < subList.size(); i++) {
                                ps.setString(i + 1, subList.get(i));
                            }

                            rs = ps.executeQuery();

                            while (rs.next()) {
                                result.put(rs.getString("uuid"), rs.getString("userId"));
                            }
                        } finally {
                            if (ps != null) {
                                ps.close();
                            }
                            if (rs != null) {
                                rs.close();
                            }
                        }
                    }
                }
            });
        } catch (SQLException e) {
            throw new ScormException("Failure looking up users for registrations", e);
        }

        return result;
    }

    public ScormUploadStatus getUploadStatus(final String siteId, final String externalId) {
        final ScormUploadStatus[] result = {null};


        try {
            DB.connection(new DBAction() {
                public void execute(Connection connection) throws SQLException {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        // Find the ID for the course
                        ps = connection.prepareStatement("select * from scs_scorm_job where siteid = ? AND externalid = ?");
                        ps.setString(1, siteId);
                        ps.setString(2, externalId);
                        rs = ps.executeQuery();

                        if (rs.next()) {
                            String message = rs.getString("error_message");

                            if (message == null) {
                                message = "";
                            }

                            result[0] = new ScormUploadStatus(rs.getInt("retry_count"), RETRY_COUNT, message);
                        }
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps != null) {
                            ps.close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            // Skip these in favor of returning a null object.
        }        

        if (result[0] == null) {
            // Return a dummy "All OK" for display purposes.  Assume the job was deleted at some point.
            return new ScormUploadStatus(0, RETRY_COUNT, "");
        } else {
            return result[0];
        }
    }

}
