package org.sakaiproject.attendance.jobs;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.attendance.logic.AttendanceLogic;


import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.sakaiproject.db.cover.SqlService;
import java.util.stream.Collectors;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;

import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.user.api.User;
import org.sakaiproject.tool.cover.ToolManager;

import java.util.Date;
import java.util.Locale;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.time.temporal.TemporalField;
import java.time.format.DateTimeFormatter;

import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.api.Site;

import org.sakaiproject.attendance.model.AttendanceEvent;
import java.time.ZoneId;

import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.authz.api.SecurityAdvisor;

import org.sakaiproject.email.cover.EmailService;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AttendancePopulator implements Job {

    private static long lastErrorTime = 0;

    private static final String ATTENDANCE_TOOL = "sakai.attendance";
    private static final String ATTENDANCE_PREPOPULATED = "attendance_prepopulated";

    private static final String ZONE = "Europe/London";
    private static final String LOCATION_CODE = "GLOBAL-0L";
    private static final int STRM = 1188;

    private static AtomicBoolean jobIsRunning = new AtomicBoolean(false);

    private static final Logger LOG = LoggerFactory.getLogger(AttendancePopulator.class);

    public void execute(JobExecutionContext context) {
        ErrorReporter errorReporter = new ErrorReporter(HotReloadConfigurationService.getString("nyu.attendance-populator.error-address", ""),
                                                        "AttendancePopulator error");


        boolean dryRunMode = "true".equals(HotReloadConfigurationService.getString("nyu.attendance-populator.dry-run-mode", "true"));

        List<String> excludeRosterList = new ArrayList(Arrays.asList(HotReloadConfigurationService.getString("nyu.attendance-populator.exclude-rosters", "").split(" *, *")));
        excludeRosterList.remove("");

        int maxReportFrequencyMs = Integer.valueOf(HotReloadConfigurationService.getString("nyu.attendance-populator.max-report-frequency-ms", "3600000"));


        if (dryRunMode) {
            LOG.warn("***\n" +
                     "*** AttendancePopulator running in dry run mode.  No attendance items will be added!\n" +
                     "***\n");
        }

        if (!jobIsRunning.compareAndSet(false, true)){
            LOG.warn("Stopping job since this job is already running");
            return;
        }

        try {
            Connection conn = SqlService.borrowConnection();
            try {
                try (PreparedStatement ps = conn.prepareStatement("select ss.site_id, cc.stem_name" +
                                                                  " from nyu_t_course_catalog cc" +
                                                                  " inner join sakai_realm_provider srp on srp.provider_id = replace(cc.stem_name, ':', '_')" +
                                                                  " inner join sakai_realm sr on sr.realm_key = srp.realm_key" +
                                                                  " inner join sakai_site ss on sr.realm_id = concat('/site/', ss.site_id)" +
                                                                  " inner join NYU_V_NON_COLLAB_SITES ncs on concat('/site/', ncs.site_id) = sr.realm_id" +
                                                                  " left join sakai_site_property ssp on ssp.site_id = ss.site_id AND ssp.name = ?" +
                                                                  " where cc.location = ? AND cc.strm >= ? AND ssp.value is null")) {
                    ps.setString(1, ATTENDANCE_PREPOPULATED);
                    ps.setString(2, LOCATION_CODE);
                    ps.setInt(3, STRM);

                    try (ResultSet rs = ps.executeQuery()) {
                        Map<String, List<String>> siteRosters = new HashMap<>();

                        while (rs.next()) {
                            String siteId = rs.getString("site_id");
                            String roster = rs.getString("stem_name");

                            if (excludeRosterList.contains(roster)) {
                                continue;
                            }

                            if (!siteRosters.containsKey(siteId)) {
                                siteRosters.put(siteId, new ArrayList<String>());
                            }

                            siteRosters.get(siteId).add(roster);
                        }

                        for (String siteId : siteRosters.keySet()) {
                            try {
                                SecurityAdvisor yesMan = new SecurityAdvisor() {
                                    public SecurityAdvice isAllowed(String userId, String function, String reference) {
                                        if ("site.upd".equals(function)) {
                                            return SecurityAdvice.ALLOWED;
                                        }

                                        return SecurityAdvice.PASS;
                                    }
                                };

                                SecurityService.pushAdvisor(yesMan);

                                try {
                                    prepopulateAttendance(conn, siteId, siteRosters.get(siteId), dryRunMode);
                                } finally {
                                    SecurityService.popAdvisor();
                                }
                            } catch (Exception e) {
                                errorReporter.addError(String.format("Error processing attendance for site %s: %s",
                                                                     siteId,
                                                                     e.toString()));
                                System.err.println(e.toString());
                                e.printStackTrace();
                            }
                        }
                    }
                }

                boolean oldAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    try (PreparedStatement ps = conn.prepareStatement("delete from nyu_t_attendance_jobs where job = 'AttendancePopulator'")) {
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement("insert into nyu_t_attendance_jobs (job, last_success_time) VALUES ('AttendancePopulator', ?)")) {
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
            errorReporter.addError("Caught exception in AttendancePopulator: " + e.toString());
            e.printStackTrace();
        } finally {
            if (!dryRunMode && (System.currentTimeMillis() - lastErrorTime) > maxReportFrequencyMs) {
                if (errorReporter.report()) {
                    lastErrorTime = System.currentTimeMillis();
                }
            }

            jobIsRunning.set(false);
        }
    }

    private static final String[] MEETING_DAYS = new String[] { "MON", "TUES", "WED", "THURS", "FRI", "SAT", "SUN" };

    private class MeetingPattern {
        public String stemName;
        public LocalDate startDate;
        public LocalDate endDate;
        public LocalTime meetingTime;
        public String holidaySchedule;
        public List<String> days = new ArrayList<>();
    }

    private Set<LocalDate> holidaysForSchedule(Connection conn, String schedule) throws Exception {
        Set<LocalDate> result = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement("select holiday" +
                                                          " from ps_holiday_date@rdb0" +
                                                          " where holiday_schedule = ?")) {
            ps.setString(1, schedule);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getDate("holiday").toLocalDate());
                }
            }
        }

        return result;
    }

    private static List<LocalDate> getDaysBetweenDates(LocalDate start, LocalDate end)
    {
        List<LocalDate> dates = new ArrayList<>();

        LocalDate day = start;

        while (!day.isAfter(end)) {
            dates.add(day);
            day = day.plusDays(1);
        }

        return dates;
    }

    private class Meeting {
        public String title;

        public LocalDate date;
        public LocalTime time;

        public Meeting(String title, LocalDate date, LocalTime time) {
            this.title = title;
            this.date = date;
            this.time = time;
        }

        public Date getStartDateTime() {
            ZoneId zone = ZoneId.of(ZONE);
            return Date.from(date.atTime(time).atZone(zone).toInstant());
        }

        public String toString() {
            return String.format("%s <%s>", title, date);
        }
    }

    private List<Meeting> meetingsForRoster(Connection conn, String rosterId) throws Exception {
        // Load meeting patterns
        List<MeetingPattern> meetingPatterns = new ArrayList<>();
        // try (PreparedStatement ps = conn.prepareStatement("select ct.holiday_schedule, mp.*" +
        //                                                   " from nyu_t_class_tbl ct" +
        //                                                   " inner join nyu_t_class_mtg_pat mp on mp.stem_name = ct.stem_name" +
        //                                                   " where mp.stem_name = ? AND mp.start_dt is not null AND mp.end_dt is not null" +
        //                                                   " order by mp.start_dt")) {

        try (PreparedStatement ps = conn.prepareStatement("select cc.stem_name, ct.holiday_schedule, mp.*" +
                                                          " from nyu_t_course_catalog cc" +
                                                          " inner join ps_class_tbl@rdb0 ct on (cc.crse_id = ct.crse_id AND cc.crse_offer_nbr = ct.crse_offer_nbr AND cc.strm = ct.strm AND cc.session_code = ct.session_code AND cc.class_section = ct.class_section)" +
                                                          " inner join ps_class_mtg_pat@rdb0 mp on (cc.crse_id = mp.crse_id AND cc.crse_offer_nbr = mp.crse_offer_nbr AND cc.strm = mp.strm AND cc.session_code = mp.session_code AND cc.class_section = mp.class_section)" +
                                                          " where cc.stem_name = ? AND mp.start_dt is not null AND mp.end_dt is not null" +
                                                          " order by mp.start_dt")) {
            ps.setString(1, rosterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MeetingPattern meetingPattern = new MeetingPattern();

                    meetingPattern.stemName = rs.getString("stem_name");
                    meetingPattern.startDate = rs.getDate("start_dt").toLocalDate();
                    meetingPattern.endDate = rs.getDate("end_dt").toLocalDate();
                    meetingPattern.meetingTime = (rs.getTimestamp("meeting_time_start") == null) ?
                        LocalTime.parse("00:00:00") :
                        rs.getTimestamp("meeting_time_start").toLocalDateTime().toLocalTime();
                    meetingPattern.holidaySchedule = rs.getString("holiday_schedule");

                    for (String day : MEETING_DAYS) {
                        if ("Y".equals(rs.getString(day))) {
                            meetingPattern.days.add(day.substring(0, 3));
                        }
                    }

                    meetingPatterns.add(meetingPattern);
                }
            }
        }

        if (meetingPatterns.size() == 0) {
            throw new RuntimeException("No meeting patterns found for roster: " + rosterId);
        }

        // Get term start date
        LocalDate termStartDate;
        try (PreparedStatement ps = conn.prepareStatement("select s.term_begin_dt" +
                                                          " from nyu_t_course_catalog cc" +
                                                          " inner join nyu_t_acad_session s on cc.strm = s.strm AND s.acad_career = cc.acad_career" +
                                                          " where cc.stem_name = ?")) {
            ps.setString(1, rosterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    termStartDate = rs.getDate("term_begin_dt").toLocalDate();
                } else {
                    throw new RuntimeException("Failed to determine term start date for roster: " + rosterId);
                }
            }
        }

        Set<LocalDate> holidays = holidaysForSchedule(conn, meetingPatterns.get(0).holidaySchedule);

        List<Meeting> result = new ArrayList<>();

        DateTimeFormatter dayOfWeek = DateTimeFormatter.ofPattern("EEE");
        Map<Integer, Integer> weekCounts = new HashMap<>();

        TemporalField weekOfYear = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear();

        for (MeetingPattern pattern : meetingPatterns) {
            for (LocalDate day : getDaysBetweenDates(pattern.startDate, pattern.endDate)) {
                if (!pattern.days.contains(dayOfWeek.format(day).toUpperCase(Locale.ROOT))) {
                    // If our meeting doesn't meet this day, skip.
                    continue;
                }

                if (holidays.contains(day)) {
                    continue;
                }

                int meetingWeek = day.get(weekOfYear) - termStartDate.get(weekOfYear) + 1;

                if (!weekCounts.containsKey(meetingWeek)) {
                    weekCounts.put(meetingWeek, 0);
                }

                weekCounts.put(meetingWeek, weekCounts.get(meetingWeek) + 1);

                Meeting m = new Meeting(String.format("Week %d Session %d",
                                                      meetingWeek, weekCounts.get(meetingWeek)),
                                        day,
                                        pattern.meetingTime);
                result.add(m);
            }
        }

        return result;
    }

    private void prepopulateAttendance(Connection conn, String siteId, List<String> rosters, boolean dryRunMode)
        throws Exception {
        List<List<Meeting>> rosterMeetings = new ArrayList<>();

        for (String rosterId : rosters) {
            rosterMeetings.add(meetingsForRoster(conn, rosterId));
        }

        if (rosterMeetings.stream().map(e -> e.size()).distinct().count() > 1) {
            throw new RuntimeException(String.format("Site %s has multiple rosters with differing meeting patterns (count mismatch)",
                                                     siteId));
        }

        for (int i = 0; i < rosterMeetings.get(0).size(); i++) {
            Set<String> titles = new HashSet<>();
            for (int r = 0; r < rosters.size(); r++) {
                titles.add(rosterMeetings.get(r).get(i).title);
            }

            if (titles.size() > 1) {
                throw new RuntimeException(String.format("Site %s has multiple rosters with differing meeting patterns (title mismatch)",
                                                         siteId));
            }
        }

        List<Meeting> siteMeetingDates = rosterMeetings.get(0);

        if (dryRunMode) {
            LOG.info("Rosters for site " + siteId + ":");
            for (String roster : rosters) {
                LOG.info("  * " + roster);
            }

            LOG.info("Meetings to be added to site: " + siteId);

            for (Meeting meeting : siteMeetingDates) {
                LOG.info(" * " + meeting);
            }

            return;
        }

        addToolIfMissing(siteId, ATTENDANCE_TOOL);

        AttendanceLogic attendanceLogic = (AttendanceLogic) ComponentManager.get("org.sakaiproject.attendance.logic.AttendanceLogic");
        AttendanceSite attendanceSite = attendanceLogic.getAttendanceSiteOrCreateIfMissing(siteId);

        Set<String> existingMeetings = attendanceLogic
            .getAttendanceEventsForSite(attendanceSite)
            .stream()
            .map(e -> e.getName())
            .collect(Collectors.toSet());

        for (Meeting meeting : siteMeetingDates) {
            if (existingMeetings.contains(meeting.title)) {
                LOG.info("Already have a meeting for: " + meeting.title);
                continue;
            }

            AttendanceEvent event = new AttendanceEvent();
            event.setAttendanceSite(attendanceSite);
            event.setName(meeting.title);
            event.setStartDateTime(meeting.getStartDateTime());

            if (attendanceLogic.addAttendanceEventNow(event) == null) {
                throw new RuntimeException(String.format("Failed to add attendance event for meeting %s in site %s",
                                                         meeting, siteId));
            }

            LOG.info(String.format("Added a new meeting to site %s: %s",
                                   siteId, meeting.title));
        }

        markSiteAsPopulated(conn, siteId);
        LOG.info("Site now fully populated: " + siteId);
    }

    // We avoid using site.getPropertiesEdit() & SiteService.save() here because
    // we might get called while site creation is in progress.  If that happens,
    // we risk trying to write properties while the site creation process is
    // doing the same, overwriting site properties with a partial set.
    //
    // Not just paranoia--this actually happened!
    //
    private void markSiteAsPopulated(Connection conn, String siteId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("insert into sakai_site_property (site_id, name, value) values (?, ?, ?)")) {
            ps.setString(1, siteId);
            ps.setString(2, ATTENDANCE_PREPOPULATED);
            ps.setString(3, "true");
            ps.executeUpdate();
        }
    }


    private void addToolIfMissing(String siteId, String registration) throws Exception {
        Site site = SiteService.getSite(siteId);

        if (site.getToolForCommonId(registration) != null) {
            return;
        }

        SitePage page = site.addPage();
        ToolConfiguration tool = page.addTool();
        tool.setTool(ATTENDANCE_TOOL, ToolManager.getTool(ATTENDANCE_TOOL));
        tool.setTitle(ToolManager.getTool(ATTENDANCE_TOOL).getTitle());

        SiteService.save(site);
    }


    private class ErrorReporter {
        private String recipientAddress;
        private String subject;
        private List<String> errors;

        public ErrorReporter(String recipientAddress, String subject) {
            this.recipientAddress = recipientAddress;
            this.subject = subject;
            this.errors = new ArrayList<>();
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public boolean report() {
            if (this.errors.isEmpty()) {
                return false;
            }

            if (this.recipientAddress.isEmpty()) {
                return false;
            }

            StringBuilder body = new StringBuilder();
            body.append("The following errors occurred while populating attendance events:\n\n");

            for (String error : this.errors) {
                body.append("  * " + error);
                body.append("\n\n");
            }

            EmailService.send(HotReloadConfigurationService.getString("nyu.overrideFromAddress", ""),
                              this.recipientAddress,
                              this.subject,
                              body.toString(),
                              null,
                              null,
                              null);

            return true;
        }
    }
}
