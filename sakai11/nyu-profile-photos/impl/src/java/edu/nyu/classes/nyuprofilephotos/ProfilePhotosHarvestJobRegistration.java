package edu.nyu.classes.nyuprofilephotos;

import org.quartz.*;
import org.sakaiproject.api.app.scheduler.JobBeanWrapper;
import org.sakaiproject.api.app.scheduler.SchedulerManager;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;


public class ProfilePhotosHarvestJobRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilePhotosHarvestJobRegistration.class);

    public void init() {
        SchedulerManager schedulerManager = (SchedulerManager) ComponentManager.get("org.sakaiproject.api.app.scheduler.SchedulerManager");
        Scheduler scheduler = schedulerManager.getScheduler();

        try {
            if (!ServerConfigurationService.getBoolean("startScheduler@org.sakaiproject.api.app.scheduler.SchedulerManager", true)) {
                LOG.info("Doing nothing because the scheduler isn't started");
                return;
            }

            registerQuartzJob(scheduler, "ProfilePhotosHarvestJob", ProfilePhotosHarvestJob.class, ServerConfigurationService.getString("profile-photos.cron", ""));
        } catch (SchedulerException e) {
            LOG.error("Error while scheduling Profile Photo Harvester job", e);
        } catch (ParseException e) {
            LOG.error("Parse error when parsing cron expression", e);
        }
    }


    private void registerQuartzJob(Scheduler scheduler, String jobName, Class className, String cronTrigger)
            throws SchedulerException, ParseException {
        JobKey key = new JobKey(jobName, Scheduler.DEFAULT_GROUP);

        scheduler.deleteJob(key);

        JobDetail detail = JobBuilder.newJob(className)
            .withIdentity(key)
            .storeDurably(true)
            .build();

        detail.getJobDataMap().put(JobBeanWrapper.SPRING_BEAN_NAME, this.getClass().toString());

        if (cronTrigger.isEmpty()) {
            LOG.info("Schedling job with no trigger: " + jobName);
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
