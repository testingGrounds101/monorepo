package org.sakaiproject.attendance.jobs;

import org.quartz.*;
import org.sakaiproject.api.app.scheduler.JobBeanWrapper;
import org.sakaiproject.api.app.scheduler.SchedulerManager;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;


public class AttendanceJobRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceJobRegistration.class);

    public void init() {
        SchedulerManager schedulerManager = (SchedulerManager) ComponentManager.get("org.sakaiproject.api.app.scheduler.SchedulerManager");

        Scheduler scheduler = schedulerManager.getScheduler();

        try {
            if (!ServerConfigurationService.getBoolean("startScheduler@org.sakaiproject.api.app.scheduler.SchedulerManager", true)) {
                LOG.info("Doing nothing because the scheduler isn't started");
                return;
            }

            registerQuartzJob(scheduler, "AttendanceGoogleReportExport", AttendanceGoogleReportJob.class, ServerConfigurationService.getString("attendance.google-export-cron", ""));
            registerQuartzJob(scheduler, "AttendancePopulator", AttendancePopulator.class, ServerConfigurationService.getString("attendance.populator-cron", ""));
        } catch (SchedulerException e) {
            LOG.error("Error while scheduling Attendance jobs", e);
        } catch (ParseException e) {
            LOG.error("Parse error when parsing cron expression", e);
        }
    }


    private void registerQuartzJob(Scheduler scheduler, String jobName, Class className, String cronTrigger)
            throws SchedulerException, ParseException {
        JobKey oldKey = new JobKey(jobName, jobName);
        JobKey key = new JobKey(jobName, Scheduler.DEFAULT_GROUP);
        // Delete any old instances of the job
        //
        // NOTE: deleting oldKey here because I've switched the group from
        // jobName to DEFAULT_GROUP to get these jobs to show up in the Job
        // Scheduler tool.  Once this change is released the oldKey stuff can be
        // removed here (but we want to keep the other deleteJob).
        scheduler.deleteJob(oldKey);

        scheduler.deleteJob(key);

        JobDetail detail = JobBuilder.newJob(className)
            .withIdentity(key)
            .storeDurably(true)
            .build();

        detail.getJobDataMap().put(JobBeanWrapper.SPRING_BEAN_NAME, this.getClass().toString());

        if (cronTrigger.isEmpty()) {
            scheduler.addJob(detail, true, true);
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(jobName + "Trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule(cronTrigger))
            .forJob(detail)
            .build();

        scheduler.scheduleJob(detail, trigger);

        LOG.info("Scheduled job: " + jobName);
    }


    public void destroy() {
    }
}
