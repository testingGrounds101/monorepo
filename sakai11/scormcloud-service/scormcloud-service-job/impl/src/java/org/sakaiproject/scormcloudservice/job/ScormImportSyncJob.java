package org.sakaiproject.scormcloudservice.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.scormcloudservice.api.ScormCloudService;
import org.sakaiproject.scormcloudservice.api.ScormException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScormImportSyncJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(ScormImportSyncJob.class);

    public void execute(JobExecutionContext context) {
        try {
            List<JobExecutionContext> jobs = context.getScheduler().getCurrentlyExecutingJobs();

            for (JobExecutionContext job : jobs) {
                if (job.getTrigger().getKey().equals(context.getTrigger().getKey()) && !job.getJobInstance().equals(this)) {
                    LOG.info("ScormImportSyncJob is already running.  Skipping this run.");
                    return;
                }
            }
        } catch (SchedulerException e) {
            LOG.error("Scheduler exception while checking job status.  Job aborting.", e);
            return;
        }

        ScormCloudService scorm = (ScormCloudService) ComponentManager.get("org.sakaiproject.scormcloudservice.api.ScormCloudService");

        try {
            scorm.runImportProcessingRound();
        } catch (ScormException e) {
            LOG.error("Failure while running SCORM Cloud service processing round", e);
        }
    }

}
