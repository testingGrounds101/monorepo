package org.sakaiproject.attendance.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.sakaiproject.attendance.logic.AttendanceLogic;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.cover.SqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;


public class AttendanceGoogleReportJob implements Job {

    private static AtomicBoolean jobIsRunning = new AtomicBoolean(false);

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceGoogleReportJob.class);

    public void execute(JobExecutionContext context) {
        if (!jobIsRunning.compareAndSet(false, true)){
            LOG.warn("Stopping job since this job is already running");
            return;
        }

        try {
            Connection conn = SqlService.borrowConnection();
            try {
                AttendanceLogic attendanceLogic = (AttendanceLogic) ComponentManager.get("org.sakaiproject.attendance.logic.AttendanceLogic");
                attendanceLogic.runGoogleReportExport();

                boolean oldAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    try (PreparedStatement ps = conn.prepareStatement("delete from nyu_t_attendance_jobs where job = 'AttendanceGoogleReportJob'")) {
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement("insert into nyu_t_attendance_jobs (job, last_success_time) VALUES ('AttendanceGoogleReportJob', ?)")) {
                        ps.setLong(1, System.currentTimeMillis());
                        ps.executeUpdate();
                    }

                    conn.commit();
                } finally {
                    conn.setAutoCommit(oldAutoCommit);
                }
            } finally {
                SqlService.returnConnection(conn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jobIsRunning.set(false);
        }
    }
}
