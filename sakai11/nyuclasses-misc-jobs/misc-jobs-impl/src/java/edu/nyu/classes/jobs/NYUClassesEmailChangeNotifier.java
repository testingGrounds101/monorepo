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
import org.sakaiproject.component.cover.ServerConfigurationService;;
import org.sakaiproject.api.app.scheduler.SchedulerManager;
import org.sakaiproject.component.cover.ComponentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

import org.sakaiproject.db.cover.SqlService;

/*

  Needs a table as follows:

    create table nyu_t_email_change_notifier (netid varchar2(255) primary key, email varchar2(255));

*/

public class NYUClassesEmailChangeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(NYUClassesEmailChangeNotifier.class);

    String JOB_NAME = "NYUClassesEmailChangeNotifier";
    String JOB_GROUP = "NYUClassesEmailChangeNotifier";

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
            String cronTrigger = ServerConfigurationService.getString("nyu.email.notifier.cron", "30 * * * * ?");

            JobDetail detail = JobBuilder.newJob(NYUClassesEmailChangeNotifierJob.class)
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


    public static class NYUClassesEmailChangeNotifierJob implements StatefulJob
    {
        private static final Logger LOG = LoggerFactory.getLogger(NYUClassesEmailChangeNotifierJob.class);

        public void execute(JobExecutionContext context) {
            LOG.info("NYUClassesEmailChangeNotifier running");

            Connection connection = null;
            PreparedStatement query = null;
            ResultSet rs = null;

            boolean oldAutoCommit;

            try {
                connection = SqlService.borrowConnection();
                oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                // Find the new and changed email addresses (there might be lots on the first run)
                //
                // Deal with duplicate entries for the same netid by grouping
                // and arbitrarily taking whichever email address we get.  It
                // shouldn't happen in production anyway, but in the dev
                // environments (where test data is managed manually) it might.
                query = connection.prepareStatement("select distinct_emails.netid, distinct_emails.email" +
                                                    " from (" +
                                                    "   select u.netid, max(u.email) email" +
                                                    "   from nyu_t_users u" +
                                                    "   group by u.netid" +
                                                    " ) distinct_emails" +
                                                    " left outer join nyu_t_email_change_notifier t" +
                                                    " on distinct_emails.netid = t.netid AND distinct_emails.email = t.email" +
                                                    " where t.netid is null");

                rs = query.executeQuery();

                while (rs.next()) {
                    List<NetIDEmail> slice = new ArrayList<>();
                    StringBuilder placeholders = new StringBuilder();

                    // Build up our next batch to work on
                    do {
                        slice.add(new NetIDEmail(rs.getString("netid"), rs.getString("email")));

                        if (placeholders.length() > 0) {
                            placeholders.append(",");
                        }

                        placeholders.append("?");

                        LOG.info("Processing email change for user: " + rs.getString("netid"));
                    } while (slice.size() < 100 && rs.next());

                    Long now = System.currentTimeMillis();

                    PreparedStatement update = null;
                    PreparedStatement insert = null;
                    PreparedStatement delete = null;

                    try {
                        update = connection.prepareStatement("update grouper_group_definitions set ready_for_sync_time = ?" +
                                " where group_id in (" +
                                "  select distinct group_id from grouper_group_users" +
                                "    where netid in (" + placeholders.toString() + ")" +
                                " )");

                        update.setLong(1, now);
                        int param = 2;
                        for (NetIDEmail e : slice) {
                            update.setString(param, e.netId);
                            param++;
                        }

                        update.executeUpdate();

                        // Delete old address entries (if they exist)
                        delete = connection.prepareStatement("delete from nyu_t_email_change_notifier" +
                                " where netid in (" + placeholders.toString() + ")");

                        param = 1;
                        for (NetIDEmail e : slice) {
                            delete.setString(param, e.netId);
                            param++;
                        }

                        delete.executeUpdate();

                        // Insert the new versions
                        insert = connection.prepareStatement("insert into nyu_t_email_change_notifier (netid, email) values (?, ?)");

                        for (NetIDEmail e : slice) {
                            insert.setString(1, e.netId);
                            insert.setString(2, e.email);
                            insert.addBatch();
                        }

                        insert.executeBatch();
                    } finally {
                        if (update != null) { update.close(); }
                        if (delete != null) { delete.close(); }
                        if (insert != null) { insert.close(); }
                    }
                }

                connection.commit();

                connection.setAutoCommit(oldAutoCommit);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                if (rs != null) {
                    try { rs.close(); } catch (SQLException e2) {};
                }

                if (query != null) {
                    try { query.close(); } catch (SQLException e2) {};
                }

                if (connection != null) {
                    SqlService.returnConnection(connection);
                }
            }

            LOG.info("NYUClassesEmailChangeNotifier finished");
        }
    }

    private static class NetIDEmail {
        String netId;
        String email;

        public NetIDEmail(String netId, String email) {
            this.netId = netId;
            this.email = email;
        }
    }
}
