package edu.nyu.classes.nyuprofilephotos;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

@DisallowConcurrentExecution
public class ProfilePhotosHarvestJob implements Job
{
    private static final Log LOG = LogFactory.getLog(ProfilePhotosHarvestJob.class);

    public void execute(JobExecutionContext context) {
        ProfilePhotosClient client = new ProfilePhotosClient(new ThumbnailWriter());

        client.authenticate();

        HarvestState state = new HarvestState();

        try {
            Date lastRunDate = state.getLastRunDate();

            if (lastRunDate == null) {
                state.storeLastRunDate(client.fullHarvest());
            } else {
                state.storeLastRunDate(client.incrementalHarvest(lastRunDate));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.logout();
        }
    }
}
