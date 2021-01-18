// Things left to do:
//
//   * DONE Email error reports to someone who cares (sticky backup etc)
//
//   * DONE clean up logging
//
//   * DONE Pull in rosters/schools/real user information from the right NYU_* tables
//     (see branch AttendanceGoogleReportJob-wip)
//
//   * Pull out config stuff into sakai.properties if useful (hot reload)
//
//   * DONE Audit FIXMEs
//
//   * DONE Target correct sheet (by name)
//
//   * DONE Add data validation to override cells
//
//   * Cell/column colors (conditional formatting might persist?)

/**********************************************************************************
 *
 * Copyright (c) 2018 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.attendance.export;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.sakaiproject.attendance.logic.AttendanceLogic;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.attendance.model.Status;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.email.cover.EmailService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttendanceGoogleReportExport {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceGoogleReportExport.class);

    private static AtomicBoolean jobIsRunning = new AtomicBoolean(false);

    private static final String APPLICATION_NAME = "AttendanceGoogleReportExport";
    private static final String BACKUP_SHEET_NAME = "_backup_";
    private String spreadsheetId;
    private Sheets service;

    private ErrorReporter errorReporter;

    public AttendanceGoogleReportExport() {
        this.errorReporter = new ErrorReporter(HotReloadConfigurationService.getString("nyu.attendance-report.error-address", ""),
                                               "AttendanceGoogleReportExport error");

        String oauthPropertiesFile = HotReloadConfigurationService.getString("attendance-report.oauth-properties", "attendance_report_oauth_properties_not_set");
        oauthPropertiesFile = ServerConfigurationService.getSakaiHomePath() + "/" + oauthPropertiesFile;

        try {
            Properties oauthProperties = new Properties();
            try (FileInputStream fis = new FileInputStream(oauthPropertiesFile)) {
                oauthProperties.load(fis);
            }

            oauthProperties.setProperty("credentials_path", new File(new File(oauthPropertiesFile).getParentFile(),
                                                                     "oauth_credentials").getPath());

            GoogleClient client = new GoogleClient(oauthProperties);
            this.service = client.getSheets(APPLICATION_NAME);

            this.spreadsheetId = HotReloadConfigurationService.getString("attendance-report.spreadsheet", "attendance_report_spreadsheet_not_set");
        } catch (Exception e) {
            errorAndThrow("Unable to initialize attendance report", e);
            this.finish();
        }
    }

    public void finish() {
        this.errorReporter.report();
    }

    private void errorAndThrow(String msg) {
        LOG.error(msg);
        errorReporter.addError(msg);
        throw new RuntimeException(msg);
    }

    private void errorAndThrow(String msg, Throwable cause) {
        LOG.error(msg + ": " + cause);
        errorReporter.addError(msg + ": " + cause);
        cause.printStackTrace();
        throw new RuntimeException(msg, cause);
    }


    abstract static class ValueObject {
        public abstract Object[] interestingFields();

        @Override
        public int hashCode() { return Arrays.hashCode(interestingFields()); }

        @Override
        public boolean equals(Object other) {
            if (this == other) { return true; }
            if (this.getClass() != other.getClass()) { return false; }
            return Arrays.equals(this.interestingFields(), ((ValueObject) other).interestingFields());
        }

        @Override
        public String toString() {
            return this.interestingFields().toString();
        }
    }

    static class SiteUser extends ValueObject implements Comparable<SiteUser> {
        public String netid;
        public String siteid;
        public String firstName;
        public String lastName;
        public String term;
        public String siteTitle;
        public String roster;

        public SiteUser(String netid, String siteid, String firstName, String lastName, String term, String siteTitle, String roster) {
            this.netid = Objects.requireNonNull(netid);
            this.siteid = Objects.requireNonNull(siteid);
            this.firstName = Objects.requireNonNull(firstName);
            this.lastName = Objects.requireNonNull(lastName);
            this.term = Objects.requireNonNull(term);
            this.siteTitle = Objects.requireNonNull(siteTitle);
            this.roster = Objects.requireNonNull(roster);
        }

        public SiteUser(String netid, String siteid) {
            this.netid = Objects.requireNonNull(netid);
            this.siteid = Objects.requireNonNull(siteid);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { netid, siteid };
        }

        @Override
        public int compareTo(SiteUser other) {
            if (this.siteid.equals(other.siteid)) {
                return this.netid.compareTo(other.netid);
            } else {
                return this.siteid.compareTo(other.siteid);
            }
        }
    }

    static class AttendanceEvent extends ValueObject implements Comparable<AttendanceEvent> {
        public String name;
        public Integer week;
        public Integer session;

        public AttendanceEvent(String name) {
            this.name = Objects.requireNonNull(name);

            String pattern = "\\w+\\s(\\d+)\\s\\w+\\s(\\d+)";
            Pattern regex = Pattern.compile(pattern);
            Matcher m = regex.matcher(this.name);
            if (m.matches()) {
                this.week = Integer.valueOf(m.group(1));
                this.session = Integer.valueOf(m.group(2));
            } else {
                this.week = new Integer(-1);
                this.session = new Integer(-1);
            }
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { name };
        }

        @Override
        public int compareTo(AttendanceEvent other) {
            if (this.week.equals(other.week)) {
                return this.session.compareTo(other.session);
            } else {
                return this.week.compareTo(other.week);
            }
        }
    }

    static class UserAtEvent extends ValueObject {
        public SiteUser user;
        public AttendanceEvent event;

        public UserAtEvent(SiteUser user, AttendanceEvent event) {
            this.user = Objects.requireNonNull(user);
            this.event = Objects.requireNonNull(event);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { user, event };
        }
    }

    static Map<String, String> statusMapping = null;
    static {
        statusMapping = new HashMap<>();
        statusMapping.put("PRESENT", "P");
        statusMapping.put("UNEXCUSED_ABSENCE", "A");
        statusMapping.put("EXCUSED_ABSENCE", "E");
        statusMapping.put("LATE", "L");
        statusMapping.put("LEFT_EARLY", "LE");
        statusMapping.put("UNKNOWN", "");
        statusMapping.put("M", "M");
        statusMapping.put("N", "N");
        statusMapping.put("R", "R");
        statusMapping.put("X", "X");
    }

    static class AttendanceOverride extends ValueObject {
        public UserAtEvent userAtEvent;
        public String override;
        public String oldStatus;
        public String rawText;

        public AttendanceOverride(UserAtEvent userAtEvent, String override, String oldStatus) {
            this.userAtEvent = Objects.requireNonNull(userAtEvent);
            this.rawText = Objects.requireNonNull(override);
            this.oldStatus = Objects.requireNonNull(oldStatus);

            for (Map.Entry<String, String> entry : statusMapping.entrySet()) {
                if (override.equals(entry.getValue())) {
                    this.override = entry.getKey();
                }
                if (oldStatus.equals(entry.getValue())) {
                    this.oldStatus = entry.getKey();
                }

            }

            if (this.override == null) {
                this.override = "";
            }
        }

        public boolean isValid() {
            return !"".equals(this.override);
        }

        public String toString() {
            return String.format("AttendanceOverride site_id: %s, netid: %s, event: %s, override: %s, replacing: %s",
                                 userAtEvent.user.siteid,
                                 userAtEvent.user.netid,
                                 userAtEvent.event.name,
                                 override,
                                 oldStatus);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { userAtEvent, override, oldStatus };
        }
    }

    static class AttendanceStoredOverride extends ValueObject {
        public UserAtEvent userAtEvent;
        public String status;

        public AttendanceStoredOverride(UserAtEvent userAtEvent, String status) {
            this.userAtEvent = Objects.requireNonNull(userAtEvent);
            this.status = Objects.requireNonNull(status);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { userAtEvent, status };
        }
    }

    static class DataTable {
        public Set<AttendanceEvent> events;
        public List<SiteUser> users;
        public Map<UserAtEvent, String> statusTable;
        public Map<UserAtEvent, AttendanceStoredOverride> overrides;

        public DataTable(List<SiteUser> users, Set<AttendanceEvent> events, Map<UserAtEvent, String> statusTable, Map<UserAtEvent, AttendanceStoredOverride> overrides) {
            this.users = users;
            this.events = events;
            this.statusTable = statusTable;
            this.overrides = overrides;
        }
    }

    private String mapStatus(String status) {
        return statusMapping.get(status);
    }



    private Optional<DataTable> loadAllData() throws Exception {
        // Get a list of all students from the sites of interest
        // Get a list of the attendance events for all sites, joined to any attendance records

        Connection conn = SqlService.borrowConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        String dbFamily = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);

        String excludedSectionsStr = HotReloadConfigurationService.getString("attendance-report.excluded-sections", "");
        List<String> excludedSections = new ArrayList<>();
        if (!excludedSectionsStr.isEmpty()) {
            for (String section : excludedSectionsStr.split(" *, *")) {
                section = section.trim();

                if (section.isEmpty()) {
                    continue;
                }

                excludedSections.add(section);
            }
        }

        try {
            // Get out list of users in sites of interest
            List<SiteUser> users = new ArrayList<>();

            Set<String> allSiteIds = new HashSet<>();

            try (PreparedStatement ps = conn.prepareStatement("SELECT usr.netid," +
                                                              "  usr.fname," +
                                                              "  usr.lname," +
                                                              "  sess.descr as term," +
                                                              "  site.title," +
                                                              "  site.site_id," +
                                                              // NOTE: The comma here is load bearing!  See below.
                                                              ("mysql".equals(dbFamily) ?
                                                               "  group_concat(srp.provider_id separator ',') provider_id" :
                                                               "  listagg(srp.provider_id, ',') within group (order by srp.provider_id) provider_id") +
                                                              " FROM nyu_t_course_catalog cc" +
                                                              " INNER JOIN nyu_t_acad_session sess ON cc.strm = sess.strm AND cc.acad_career = sess.acad_career AND sess.current_flag = 'Y'" +
                                                              " INNER JOIN nyu_t_student_enrollments se on se.stem_name = cc.stem_name" +
                                                              " INNER JOIN sakai_realm_provider srp on srp.provider_id = REPLACE(cc.stem_name, ':', '_')" +
                                                              " INNER JOIN sakai_realm rlm ON rlm.realm_key = srp.realm_key" +
                                                              " INNER JOIN NYU_V_NON_COLLAB_SITES ncs on concat('/site/', ncs.site_id) = rlm.realm_id" +
                                                              " INNER JOIN sakai_site site on CONCAT('/site/', site.site_id) = rlm.realm_id" +
                                                              " INNER JOIN attendance_site_t att ON att.site_id = site.site_id" +
                                                              " INNER JOIN nyu_t_users usr ON usr.netid = se.netid" +
                                                              " WHERE cc.location in ('GLOBAL-0L')" +
                                                              " GROUP BY usr.netid, usr.fname, usr.lname, sess.descr, site.title, site.site_id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // NOTE: We're assuming that the comma was inserted by the
                    // SQL above.  If you change one, change the other.

                    // If the student entry only consists of excluded sections, skip the whole row
                    if (Arrays.stream(rs.getString("provider_id").split(","))
                        .allMatch(s -> excludedSections.contains(s))) {
                        continue;
                    }

                    users.add(new SiteUser(rs.getString("netid"),
                                           rs.getString("site_id"),
                                           rs.getString("fname"),
                                           rs.getString("lname"),
                                           rs.getString("term"),
                                           rs.getString("title"),
                                           rs.getString("provider_id")));

                    allSiteIds.add(rs.getString("site_id"));
                }
            }

            if (allSiteIds.isEmpty()) {
                return Optional.empty();
            }


            Set<AttendanceEvent> events = new HashSet<>();
            Map<UserAtEvent, String> statusTable = new HashMap<>();


            for (Set<String> siteIds : partitionSet(allSiteIds, 512)) {
                // String siteIdQueryString = siteIds.stream().map(n -> String.format("'%s'", n)).collect(Collectors.joining(","));
                String placeholders = siteIds.stream().map(_p -> "?").collect(Collectors.joining(","));

                // Get our mapping of events to the sites that have them
                Map<AttendanceEvent, Set<String>> sitesWithEvent = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement("select e.name, s.site_id" +
                                                                  " from attendance_event_t e" +
                                                                  " inner join attendance_site_t s on s.a_site_id = e.a_site_id" +
                                                                  " where s.site_id in (" + placeholders + ")")) {
                    Iterator<String> it = siteIds.iterator();
                    for (int i = 0; it.hasNext(); i++) {
                        ps.setString(i + 1, it.next());
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            AttendanceEvent event = new AttendanceEvent(rs.getString("name"));

                            if (!sitesWithEvent.containsKey(event)) {
                                sitesWithEvent.put(event, new HashSet<>());
                            }

                            sitesWithEvent.get(event).add(rs.getString("site_id"));
                        }

                    }
                }

                events.addAll(sitesWithEvent.keySet());

                // If a user is in a site that doesn't have a particular event, that's a "-"
                for (SiteUser user : users) {
                    for (AttendanceEvent event : events) {
                        if (!sitesWithEvent.get(event).contains(user.siteid)) {
                            statusTable.put(new UserAtEvent(user, event), "-");
                        }
                    }
                }

                // Get all users at all events
                try (PreparedStatement ps = conn.prepareStatement("select s.site_id, e.name, m.eid, r.status" +
                                                                  " from attendance_event_t e" +
                                                                  " inner join attendance_record_t r on e.a_event_id = r.a_event_id" +
                                                                  " inner join attendance_site_t s on e.a_site_id = s.a_site_id" +
                                                                  " inner join sakai_user_id_map m on m.user_id = r.user_id" +
                                                                  " where s.site_id in (" + placeholders + ")")) {
                    Iterator<String> it = siteIds.iterator();
                    for (int i = 0; it.hasNext(); i++) {
                        ps.setString(i + 1, it.next());
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        // Fill out the values we know
                        while (rs.next()) {
                            SiteUser user = new SiteUser(rs.getString("eid"), rs.getString("site_id"));
                            AttendanceEvent event = new AttendanceEvent(rs.getString("name"));

                            String status = mapStatus(rs.getString("status"));

                            if (status != null) {
                                statusTable.put(new UserAtEvent(user, event), status);
                            }
                        }
                    }
                }
            }

            // And fill out any that were missing as UNKNOWN/empty strings
            for (SiteUser user : users) {
                for (AttendanceEvent event : events) {
                    UserAtEvent key = new UserAtEvent(user, event);

                    if (!statusTable.containsKey(key)) {
                        statusTable.put(key, "");
                    }
                }
            }

            Map<UserAtEvent, AttendanceStoredOverride> overrides = getStoredOverrides(conn);

            return Optional.of(new DataTable(users, events, statusTable, overrides));
        } finally {
            conn.setAutoCommit(oldAutoCommit);
            SqlService.returnConnection(conn);
        }
    }

    // Group a set of items into subsets of no more than partitionSize
    private <T> List<Set<T>> partitionSet(Set<T> items, int partitionSize) {
        if (partitionSize <= 0) {
            throw new IllegalArgumentException("partitionSize must be positive");
        }

        List<Set<T>> result = new ArrayList<>();

        if (items.isEmpty()) {
            return result;
        }

        Iterator<T> it = items.iterator();

        for (int i = 0; it.hasNext(); i++) {
            if ((i % partitionSize) == 0) {
                result.add(new HashSet<T>());
            }

            result.get(result.size() - 1).add(it.next());
        }

        return result;
    }

    public void export() throws Exception {
        try {
            Sheet sheet = getTargetSheet();

            if (backupExists()) {
                errorAndThrow("Backup sheet exists! Stop everything!");
            }

            if (!loadAllData().isPresent()) {
                errorAndThrow("No data to report.  Leaving current sheet alone.");
            }

            backupSheet(sheet);

            clearProtectedRanges(sheet);
            ProtectedRange range = protectSheet(sheet.getProperties().getSheetId());

            storeOverrides(pullOverrides(sheet));
            Optional<DataTable> table = loadAllData(); // this must run after pulling the overrides
            clearValidations(sheet);
            clearSheet(sheet);
            syncValuesToSheet(sheet, table.get());
            applyColumnAndCellProperties(sheet, range);
            deleteSheet(BACKUP_SHEET_NAME);
            unprotectRange(sheet, range);
        } catch (Exception e) {
            errorAndThrow("ERROR in AttendanceGoogleReportExport.export", e);
        } finally {
            this.finish();
        }
    }

    private boolean backupExists() throws IOException {
        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if (BACKUP_SHEET_NAME.equals(sheet.getProperties().getTitle())) {
                return true;
            }
        }

        return false;
    }

    private void deleteSheet(String sheetName) throws IOException {
        LOG.debug("Delete sheet: " + sheetName);

        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();
        List<Request> requests = new ArrayList<>();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                DeleteSheetRequest deleteSheetRequest = new DeleteSheetRequest();
                deleteSheetRequest.setSheetId(sheet.getProperties().getSheetId());

                Request request = new Request();
                request.setDeleteSheet(deleteSheetRequest);
                requests.add(request);
            }
        }

        if (requests.isEmpty()) {
            return;
        }

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
    }

    private void backupSheet(Sheet sheet) throws IOException {
        LOG.debug("Backup sheet");

        DuplicateSheetRequest duplicateSheetRequest = new DuplicateSheetRequest();
        duplicateSheetRequest.setSourceSheetId(sheet.getProperties().getSheetId());
        duplicateSheetRequest.setInsertSheetIndex(1);
        duplicateSheetRequest.setNewSheetName(BACKUP_SHEET_NAME);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        List<Request> requests = new ArrayList<>();
        Request request = new Request();
        request.setDuplicateSheet(duplicateSheetRequest);
        requests.add(request);
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
        Response response = batchUpdateSpreadsheetResponse.getReplies().get(0);

        DuplicateSheetResponse duplicateSheetResponse = response.getDuplicateSheet();
        protectSheet(duplicateSheetResponse.getProperties().getSheetId());
    }

    private void storeOverrides(List<AttendanceOverride> overrides) throws Exception {
        LOG.debug("Store overrides");
        // netid, siteid, event name...
         AttendanceLogic attendance = (AttendanceLogic) ComponentManager.get("org.sakaiproject.attendance.logic.AttendanceLogic");

        Connection conn = SqlService.borrowConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // Delete any existing overrides
            try (PreparedStatement ps = conn.prepareStatement("delete from attendance_record_override_t")) {
                 ps.executeUpdate();
            }

            for (AttendanceOverride override : overrides) {
                if (!override.isValid()) {
                    errorReporter.addError("Invalid override: " + override.rawText);
                    continue;
                }

                LOG.info(override.toString());

                boolean updated = false;

                String netid = override.userAtEvent.user.netid;
                try {
                    User user = UserDirectoryService.getUserByEid(netid);

                    AttendanceSite attendanceSite = attendance.getAttendanceSite(override.userAtEvent.user.siteid);

                    List<AttendanceRecord> records =
                        attendance
                        .getAttendanceRecordsForUsers(Collections.singletonList(user.getId()),
                                                      attendanceSite)
                        .get(user.getId());

                    if (records == null) {
                        records = Collections.emptyList();
                    }

                    for (AttendanceRecord record : records) {
                        if (override.userAtEvent.event.name.equals(record.getAttendanceEvent().getName())) {
                            Status oldStatus = record.getStatus();

                            try {
                                // Insert the override to our magic table
                                try (PreparedStatement ps = conn.prepareStatement("insert into attendance_record_override_t (netid, site_id, event_name, status)" +
                                                                                  " values (?, ?, ?, ?)")) {
                                    ps.setString(1, netid);
                                    ps.setString(2, override.userAtEvent.user.siteid);
                                    ps.setString(3, record.getAttendanceEvent().getName());
                                    ps.setString(4, override.override);

                                    if (ps.executeUpdate() == 0) {
                                        errorReporter.addError("Failed to store override for netid:" + netid +
                                            " eventId: " + String.valueOf(record.getAttendanceEvent().getId()) +
                                            " siteId: " + override.userAtEvent.user.siteid +
                                            " override: " + override.override);
                                    }
                                }
                            } catch (Exception e) {
                                errorAndThrow("Error updating attendance report override", e);
                            }
                            updated = true;
                            break;
                        }
                    }

                    if (!updated) {
                        errorReporter.addError("Failed to store override for netid:" + netid +
                                               " event: " + override.userAtEvent.event.name +
                                               " siteId: " + override.userAtEvent.user.siteid +
                                               " override: " + override.override);
                    }
                } catch (UserNotDefinedException e) {
                    errorAndThrow(String.format("Failed to match user '%s'", netid), e);
                }
            }

            conn.commit();
        }  finally {
            conn.setAutoCommit(oldAutoCommit);
            SqlService.returnConnection(conn);
        }
    }

    private Map<UserAtEvent,AttendanceStoredOverride> getStoredOverrides(Connection conn) throws Exception {
        Map<UserAtEvent,AttendanceStoredOverride> result = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement("select netid, site_id, event_name, status" +
                                                          " from attendance_record_override_t");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AttendanceEvent event = new AttendanceEvent(rs.getString("event_name"));
                SiteUser user = new SiteUser(rs.getString("netid"), rs.getString("site_id"));
                UserAtEvent userAtEvent = new UserAtEvent(user, event);
                result.put(userAtEvent, new AttendanceStoredOverride(userAtEvent, rs.getString("status")));
            }
        }

        return result;
    }

    private List<AttendanceOverride> pullOverrides(Sheet sheet) throws IOException {
        LOG.debug("Pull overrides");
        Sheets.Spreadsheets.Values.Get request = service.spreadsheets().values().get(spreadsheetId, sheet.getProperties().getTitle());
        ValueRange values = request.execute();

        // handle empty spreadsheet
        if (values.getValues() == null || values.getValues().isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> headers = values.getValues().get(0);

        String[] overrideEvents = new String[headers.size()];

        for (int i = 0; i < headers.size(); i++) {
            if (((String) headers.get(i)).endsWith("\nOVERRIDE")) {
                String eventName = (String) headers.get(i - 1);
                overrideEvents[i] = eventName;
            }
        }

        List<AttendanceOverride> result = new ArrayList<>();

        for (int i = 1; i < values.getValues().size(); i++) {
            List<Object> row = values.getValues().get(i);
            String netId = (String)row.get(0);
            String siteUrl = (String)row.get(6);

            for (int override = 0; override < overrideEvents.length && override < row.size(); override++) {
                if (overrideEvents[override] == null) {
                    // This isn't an override column.  Ignore.
                    continue;
                }

                if ("-".equals(row.get(override - 1))) {
                    // You can't override an event the student isn't in.
                    continue;
                }

                if (row.get(override) == null || "".equals(row.get(override))) {
                    // No override specified for this student.
                    continue;
                }

                SiteUser user = new SiteUser(netId, siteUrl.replaceAll(".*/", ""));
                AttendanceEvent event = new AttendanceEvent(overrideEvents[override]);
                UserAtEvent userAtEvent = new UserAtEvent(user, event);

                result.add(new AttendanceOverride(userAtEvent, (String)row.get(override), (String)row.get(override - 1)));
            }
        }

        LOG.debug("- overrides found: " + result.size());

        return result;
    }

    private ProtectedRange protectSheet(Integer sheetId) throws IOException {
        List<Request> requests = new ArrayList<>();

        LOG.debug("Protect sheet: " + sheetId);
        AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
        ProtectedRange protectedRange = new ProtectedRange();
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(sheetId);
        protectedRange.setRange(gridRange);
        protectedRange.setEditors(new Editors());
        protectedRange.setRequestingUserCanEdit(true);
        addProtectedRangeRequest.setProtectedRange(protectedRange);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        Request wrapperRequest = new Request();
        wrapperRequest.setAddProtectedRange(addProtectedRangeRequest);
        requests.add(wrapperRequest);
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
        for (Response response : batchUpdateSpreadsheetResponse.getReplies()) {
            AddProtectedRangeResponse addProtectedRangeResponse = response.getAddProtectedRange();
            if (addProtectedRangeResponse != null) {
                return addProtectedRangeResponse.getProtectedRange();
            }
        }

        errorAndThrow("No protected range returned after protectSheet");

        // Never reached
        return null;
    }

    private void clearProtectedRanges(Sheet sheet) throws IOException {
        Integer sheetId = sheet.getProperties().getSheetId();

        LOG.debug("Delete any protected ranges from sheet: " + sheetId);
        List<Request> requests = new ArrayList<>();
        List<ProtectedRange> protectedRanges = sheet.getProtectedRanges();
        if (protectedRanges != null) {
            for (ProtectedRange protectedRange : protectedRanges) {
                DeleteProtectedRangeRequest deleteProtectedRangeRequest = new DeleteProtectedRangeRequest();
                deleteProtectedRangeRequest.setProtectedRangeId(protectedRange.getProtectedRangeId());
                Request request = new Request();
                request.setDeleteProtectedRange(deleteProtectedRangeRequest);
                requests.add(request);
            }
        }

        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
            batchUpdateSpreadsheetRequest.setRequests(requests);
            Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);
            LOG.debug(batchUpdateRequest.execute().toString());
        }
    }

    private void clearValidations(Sheet sheet) throws IOException {
        Integer sheetId = sheet.getProperties().getSheetId();

        List<Request> requests = new ArrayList<>();

        LOG.debug("Delete any data validations from sheet: " + sheetId);
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(sheetId);
        SetDataValidationRequest setDataValidationRequest = new SetDataValidationRequest();
        setDataValidationRequest.setRange(gridRange);
        setDataValidationRequest.setRule(null);
        Request request = new Request();
        request.setSetDataValidation(setDataValidationRequest);
        requests.add(request);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);
        LOG.debug(batchUpdateRequest.execute().toString());
    }

    private void unprotectRange(Sheet sheet, ProtectedRange range) throws IOException {
        List<Request> requests = new ArrayList<>();

        DeleteProtectedRangeRequest deleteProtectedRangeRequest = new DeleteProtectedRangeRequest();
        deleteProtectedRangeRequest.setProtectedRangeId(range.getProtectedRangeId());
        Request request = new Request();
        request.setDeleteProtectedRange(deleteProtectedRangeRequest);
        requests.add(request);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest = service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);
        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
    }

    private void clearSheet(Sheet sheet) throws IOException {
        LOG.debug("Clear the sheet");
        Sheets.Spreadsheets.Values.Clear clearRequest =
            service.spreadsheets().values().clear(spreadsheetId, sheet.getProperties().getTitle(), new ClearValuesRequest());
        ClearValuesResponse clearValuesResponse = clearRequest.execute();
    }

    private void syncValuesToSheet(Sheet sheet, DataTable table) throws Exception {
        LOG.debug("Give it some values");
        ValueRange valueRange = new ValueRange();
        List<List<Object>> rows = new ArrayList<>();

        // Add a row for our header
        List<Object> header = new ArrayList<>();
        header.add("NetID");
        header.add("Last Name");
        header.add("First Name");
        header.add("Term");
        header.add("Course Title");
        header.add("Roster ID");
        header.add("Site URL");

        List<AttendanceEvent> sortedEvents = new ArrayList(table.events);
        Collections.sort(sortedEvents);

        for (AttendanceEvent event : sortedEvents) {
            header.add(event.name);
            header.add(event.name + "\nOVERRIDE");
        }
        rows.add(header);

        List<SiteUser> sortedUsers = new ArrayList(table.users);
        Collections.sort(sortedUsers);

        // Now our student data
        for (SiteUser user : sortedUsers) {
            List<Object> row = new ArrayList<>();
            row.add(user.netid);
            row.add(user.lastName);
            row.add(user.firstName);
            row.add(user.term);
            row.add(user.siteTitle);
            row.add(user.roster);
            row.add("https://newclasses.nyu.edu/portal/site/" + user.siteid);

            for (AttendanceEvent event : sortedEvents) {
                UserAtEvent userAtEvent = new UserAtEvent(user, event);
                row.add(table.statusTable.get(userAtEvent));

                if (table.overrides.containsKey(userAtEvent)) {
                    String mappedOverride = mapStatus(table.overrides.get(userAtEvent).status);
                    if (mappedOverride == null) {
                        row.add("");
                    } else {
                        row.add(mappedOverride);
                    }
                } else {
                    row.add("");
                }
            }

            rows.add(row);
        }

        valueRange.setValues(rows);

        Sheets.Spreadsheets.Values.Update updateRequest =
            service.spreadsheets().values().update(spreadsheetId, sheet.getProperties().getTitle() + "!A1:ZZ", valueRange);
        updateRequest.setValueInputOption("RAW");
        UpdateValuesResponse updateValuesResponse = updateRequest.execute();
    }

    private Sheet getTargetSheet() throws IOException {
        LOG.debug("Get the sheet");
        List<String> ranges = new ArrayList<>();
        Sheets.Spreadsheets.Get request = service.spreadsheets().get(spreadsheetId);
        request.setRanges(ranges);
        request.setIncludeGridData(false);
        Spreadsheet spreadsheet = request.execute();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if ("Edit Mode".equals(sheet.getProperties().getTitle())) {
                return sheet;
            }
        }

        errorAndThrow("Could not find 'Edit Mode' sheet");

        // Never reached
        return null;
    }

    private void applyColumnAndCellProperties(Sheet targetSheet, ProtectedRange sheetProtectedRange) throws Exception {
        LOG.debug("Apply column and cell properties");

        // All requests to apply to the spreadsheet
        List<Request> requests = new ArrayList<>();

        // Build requests to drop all existing protected ranges (except sheetProtectingRange)
        LOG.debug("- get existing protected ranges for deletion");
        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (targetSheet.getProperties().getSheetId().equals(sheet.getProperties().getSheetId())) {
                for (ProtectedRange protectedRange : sheet.getProtectedRanges()) {
                    if (sheetProtectedRange.getProtectedRangeId().equals(protectedRange.getProtectedRangeId())) {
                        continue;
                    }

                    DeleteProtectedRangeRequest deleteProtectedRangeRequest = new DeleteProtectedRangeRequest();
                    deleteProtectedRangeRequest.setProtectedRangeId(protectedRange.getProtectedRangeId());
                    Request request = new Request();
                    request.setDeleteProtectedRange(deleteProtectedRangeRequest);
                    requests.add(request);
                }
            }
        }

        // Build requests to protected each non-OVERRIDE column
        LOG.debug("- build new protected ranges from headers");
        Sheets.Spreadsheets.Values.Get spreadsheetGetRequest = service.spreadsheets().values().get(spreadsheetId, targetSheet.getProperties().getTitle() + "!A1:ZZ1");
        ValueRange values = spreadsheetGetRequest.execute();

        List<Object> headers = values.getValues().get(0);
        for (int i=0; i < headers.size(); i++) {
            String header = (String) headers.get(i);
            if (header.endsWith("\nOVERRIDE")) {
                continue;
            }

            ProtectedRange protectedRange = new ProtectedRange();
            GridRange gridRange = new GridRange();
            gridRange.setSheetId(targetSheet.getProperties().getSheetId());
            gridRange.setStartColumnIndex(i);
            gridRange.setEndColumnIndex(i+1);
            protectedRange.setRange(gridRange);
            protectedRange.setEditors(new Editors());
            protectedRange.setRequestingUserCanEdit(true);

            AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
            addProtectedRangeRequest.setProtectedRange(protectedRange);

            Request request = new Request();
            request.setAddProtectedRange(addProtectedRangeRequest);
            requests.add(request);
        }

        // protect the header row
        ProtectedRange protectedRange = new ProtectedRange();
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(targetSheet.getProperties().getSheetId());
        gridRange.setStartRowIndex(0);
        gridRange.setEndRowIndex(1);
        protectedRange.setRange(gridRange);
        protectedRange.setEditors(new Editors());
        protectedRange.setRequestingUserCanEdit(true);
        AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
        addProtectedRangeRequest.setProtectedRange(protectedRange);
        Request request = new Request();
        request.setAddProtectedRange(addProtectedRangeRequest);
        requests.add(request);

        LOG.debug("- add data validation to override columns");
        DataValidationRule validationRule = new DataValidationRule();
        BooleanCondition booleanCondition = new BooleanCondition();
        booleanCondition.setType("ONE_OF_LIST");

        List<ConditionValue> overrideValues = new ArrayList<>();
        ConditionValue conditionValue = new ConditionValue();

        for (String status : new String[] { "P", "A", "E", "M", "N", "R", "X" }) {
            conditionValue = new ConditionValue();
            conditionValue.setUserEnteredValue(status);
            overrideValues.add(conditionValue);
        }

        booleanCondition.setValues(overrideValues);

        validationRule.setCondition(booleanCondition);
        validationRule.setStrict(true);
        validationRule.setInputMessage("Select an override value from the list of available options.");
        validationRule.setShowCustomUi(true);

        for (int i=0; i < headers.size(); i++) {
            String header = (String) headers.get(i);
            if (!header.endsWith("\nOVERRIDE")) {
                continue;
            }
            gridRange = new GridRange();
            gridRange.setSheetId(targetSheet.getProperties().getSheetId());
            gridRange.setStartColumnIndex(i);
            gridRange.setEndColumnIndex(i+1);
            gridRange.setStartRowIndex(1); // not the header
            SetDataValidationRequest setDataValidationRequest = new SetDataValidationRequest();
            setDataValidationRequest.setRange(gridRange);
            setDataValidationRequest.setRule(validationRule);
            request = new Request();
            request.setSetDataValidation(setDataValidationRequest);
            requests.add(request);
        }

        runRequestsInSmallBatches(requests);
    }

    // Break `requests` into small batches, and try each batch up to 5 times before bailing out.
    private void runRequestsInSmallBatches(List<Request> requests) throws Exception {
        int maxRetries = 5;
        int batchSize = 16;
        List<Request> batch = new ArrayList(batchSize);

        for (int i = 0; i < requests.size(); i += batchSize) {
            BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
            batchUpdateSpreadsheetRequest.setRequests(requests.subList(i, Math.min(i + batchSize, requests.size())));

            Exception lastException = null;
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    lastException = null;
                    service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
                    break;
                } catch (Exception e) {
                    lastException = e;
                    try {
                        Thread.sleep((long)(Math.pow(2, retry) * 1000) +
                                     (long)(Math.random() * 1000));
                    } catch (InterruptedException e2) {}
                }
            }

            if (lastException != null) {
                throw lastException;
            }
        }
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
            body.append("The following errors occurred while producing the Attendance report:\n\n");

            for (String error : this.errors) {
                body.append("  * " + error);
                body.append("\n\n");
            }

            LOG.error("AttendanceGoogleReportExport: " + body.toString());

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
