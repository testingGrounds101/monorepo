package org.sakaiproject.scormcloudservice.job;

import org.quartz.*;
import org.sakaiproject.api.app.scheduler.JobBeanWrapper;
import org.sakaiproject.api.app.scheduler.SchedulerManager;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;


public class ScormSyncJobRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(ScormSyncJobRegistration.class);

    public void init() {
        SchedulerManager schedulerManager = (SchedulerManager) ComponentManager.get("org.sakaiproject.api.app.scheduler.SchedulerManager");

        Scheduler scheduler = schedulerManager.getScheduler();

        try {
            if (!ServerConfigurationService.getBoolean("startScheduler@org.sakaiproject.api.app.scheduler.SchedulerManager", true)) {
                LOG.info("Doing nothing because the scheduler isn't started");
                return;
            }

            registerQuartzJob(scheduler, "ScormImportSyncJob", ScormImportSyncJob.class, ServerConfigurationService.getString("scormcloudservice.import-job-cron", "0 * * * * ?"));
            registerQuartzJob(scheduler, "ScormScoreSyncJob", ScormScoreSyncJob.class, ServerConfigurationService.getString("scormcloudservice.score-job-cron", "0 * * * * ?"));
        } catch (SchedulerException e) {
            LOG.error("Error while scheduling Scorm Cloud Service sync job", e);
        } catch (ParseException e) {
            LOG.error("Parse error when parsing cron expression", e);
        }
    }


    private void registerQuartzJob(Scheduler scheduler, String jobName, Class className, String cronTrigger)
            throws SchedulerException, ParseException {
        // Delete any old instances of the job
        scheduler.deleteJob(new JobKey(jobName, jobName));

        JobDetail detail = JobBuilder.newJob(className)
            .withIdentity(jobName, jobName)
            .build();

        detail.getJobDataMap().put(JobBeanWrapper.SPRING_BEAN_NAME, this.getClass().toString());

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(jobName + "Trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule(cronTrigger))
            .forJob(detail)
            .build();

        scheduler.scheduleJob(detail, trigger);

        LOG.info("Scheduled Scorm job: " + jobName);
    }


    public void destroy() {
    }
}
