package edu.nyu.classes.jobtest.impl;

import java.io.FileWriter;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import org.sakaiproject.component.cover.ServerConfigurationService;;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class NYUClassesTestJob implements Job
{
    private static final Logger LOG = LoggerFactory.getLogger(NYUClassesTestJob.class);


    public void execute(JobExecutionContext context) {
        String OUTPUT_PATH_PROPERTY = "nyu.test.job.output-file";

        String outputFilePath = ServerConfigurationService.getString(OUTPUT_PATH_PROPERTY, null);

        if (outputFilePath == null) {
            LOG.error(OUTPUT_PATH_PROPERTY + " is not set.  Job skipped.");
            return;
        }

        try {
            touchFile(outputFilePath);
        } catch (Exception e) {
            LOG.error("Failed to update status file", e);
        }
    }


    private void touchFile(String path) throws Exception {
        FileWriter out = null;

        try {
            out = new FileWriter(path);

            out.write(String.valueOf(System.currentTimeMillis()));
            out.write("\n");

            LOG.info("Wrote status file");
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }


}
