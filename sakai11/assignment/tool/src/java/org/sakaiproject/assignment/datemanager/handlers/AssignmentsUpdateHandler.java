package org.sakaiproject.assignment.datemanager.handlers;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.cover.CalendarService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.cover.PreferencesService;
import org.sakaiproject.util.ResourceLoader;
import java.util.function.Function;

public class AssignmentsUpdateHandler implements Handler {
    private String redirectTo = null;

    private class AssignmentUpdate {
        public Assignment assignment;
        public Instant openDate;
        public Instant dueDate;
        public Instant acceptUntilDate;
        public boolean published;

        public AssignmentUpdate(Assignment assignment, Instant openDate, Instant dueDate, Instant acceptUntilDate, boolean published) {
            this.assignment = assignment;
            this.openDate = openDate;
            this.dueDate = dueDate;
            this.acceptUntilDate = acceptUntilDate;
            this.published = published;
        }
    }

    private class Error {
        public String field;
        public String msg;
        public int idx;

        public Error(String field, String msg, int idx) {
            this.field = field;
            this.msg = msg;
            this.idx = idx;
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            String siteId = (String)context.get("siteId");

            AssignmentService assignmentService = ComponentManager.get(AssignmentService.class);

            RequestParams p = new RequestParams(request);

            Object json = new JSONParser().parse(p.getString("json", "[]"));

            if (!(json instanceof JSONArray)) {
                throw new RuntimeException("Parse failed");
            }

            JSONArray assignments = (JSONArray) json;

            List<AssignmentUpdate> updates = new ArrayList<>(assignments.size());
            final List<Error> errors = new ArrayList<>();

            for (int i = 0; i < assignments.size(); i++) {
                JSONObject jsonAssignment = (JSONObject)assignments.get(i);

                String assignmentId = (String)jsonAssignment.get("id");

                if (assignmentId == null) {
                    errors.add(new Error("assignment", "Assignment could not be found", i));
                    continue;
                }

                String assignmentReference = assignmentService.assignmentReference(siteId, assignmentId);
                boolean canUpdate = assignmentService.allowUpdateAssignment(assignmentReference);

                if (!canUpdate) {
                    errors.add(new Error("assignment", "Update permission denied", i));
                    continue;
                }

                TimeZone userTimeZone = getUserTimeZone();

                Instant openDate = parseStringToInstant((String)jsonAssignment.get("open_date"), userTimeZone);
                Instant dueDate = parseStringToInstant((String)jsonAssignment.get("due_date"), userTimeZone);
                Instant acceptUntil = parseStringToInstant((String)jsonAssignment.get("accept_until"), userTimeZone);

                boolean errored = false;

                if (openDate == null) {
                    errors.add(new Error("open_date", "Could not read Open date", i));
                    errored = true;
                }
                if (dueDate == null) {
                    errors.add(new Error("due_date", "Could not read Due date", i));
                    errored = true;
                }
                if (acceptUntil == null) {
                    errors.add(new Error("accept_until", "Could not read Accept Until date", i));
                    errored = true;
                }

                if (errored) {
                    continue;
                }

                Assignment assignment = assignmentService.getAssignment(assignmentId);

                if (assignment == null) {
                    errors.add(new Error("assignment", "Assignment could not be found", i));
                    continue;
                }

                AssignmentUpdate update = new AssignmentUpdate(assignment,
                                                               openDate, dueDate, acceptUntil,
                                                               (Boolean)jsonAssignment.get("published"));

                if (!update.published && !assignment.getDraft()) {
                    errors.add(new Error("assignment", "Can't unpublish an already published assignment", i));
                    continue;
                }

                if (!update.openDate.isBefore(update.dueDate)) {
                    errors.add(new Error("open_date", "Open date must fall before due date", i));
                    continue;
                }

                if (update.dueDate.isAfter(update.acceptUntilDate)) {
                    errors.add(new Error("due_date", "Due date cannot fall after Accept Until date", i));
                    continue;
                }

                updates.add(update);
            }

            if (errors.isEmpty()) {
                for (AssignmentUpdate update : updates) {
                    Assignment assignment = update.assignment;

                    assignment.setOpenDate(update.openDate);
                    assignment.setDueDate(update.dueDate);
                    assignment.setCloseDate(update.acceptUntilDate);
                    assignment.setDraft(!update.published);

                    assignmentService.updateAssignment(assignment);

                    syncWithCalendar(siteId, assignment);
                }

                response.getWriter().write("{\"status\": \"OK\"}");
            } else {
                JSONArray errorReport = new JSONArray();

                for (Error error : errors) {
                    JSONObject jsonError = new JSONObject();

                    jsonError.put("field", error.field);
                    jsonError.put("msg", error.msg);
                    jsonError.put("idx", error.idx);

                    errorReport.add(jsonError);
                }

                response.getWriter().write(String.format("{\"status\": \"ERROR\", \"errors\": %s}",
                                                         errorReport.toJSONString()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void syncWithCalendar(String siteId, Assignment assignment) {
        Map<String, String> props = assignment.getProperties();
        if (props.containsKey(ResourceProperties.PROP_ASSIGNMENT_DUEDATE_CALENDAR_EVENT_ID)) {
            String eventId = props.get(ResourceProperties.PROP_ASSIGNMENT_DUEDATE_CALENDAR_EVENT_ID);
            try {
                Calendar calendar = CalendarService.getCalendar(CalendarService.calendarReference(siteId, SiteService.MAIN_CONTAINER));
                CalendarEventEdit event = calendar.getEditEvent(eventId, org.sakaiproject.calendar.api.CalendarService.EVENT_MODIFY_CALENDAR_EVENT_TIME);
                TimeRange newEventDate = org.sakaiproject.time.cover.TimeService.newTimeRange(assignment.getDueDate().toEpochMilli(), 0);
                event.setRange(newEventDate);
                calendar.commitEvent(event);
            } catch (IdUnusedException e) {
                // oh well, we tried
            } catch (PermissionException e) {
                // oh well, we tried
            } catch (InUseException e) {
                // oh well, we tried
            }
        }
    }

    private Instant parseStringToInstant(String timestamp, TimeZone userTimeZone) {
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(userTimeZone.toZoneId())
                .parse(timestamp, Instant::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // FIXME: pull these out
    private String getCurrentUserId() {
        return SessionManager.getCurrentSessionUserId();
    }

    private Locale getUserLocale() {
        final ResourceLoader rl = new ResourceLoader();
        return rl.getLocale();
    }

    private TimeZone getUserTimeZone() {
        TimeZone timezone;
        final Preferences prefs = PreferencesService.getPreferences(getCurrentUserId());
        final ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
        final String tzPref = props.getProperty(TimeService.TIMEZONE_KEY);

        if (StringUtils.isNotBlank(tzPref)) {
            timezone = TimeZone.getTimeZone(tzPref);
        } else {
            timezone = TimeZone.getDefault();
        }

        return timezone;
    }

    public boolean hasRedirect() {
        return (redirectTo != null);
    }

    public String getRedirect() {
        return redirectTo;
    }

    public Errors getErrors() {
        return null;
    }

    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    @Override
    public String getContentType() {
        return "text/json";
    }

    @Override
    public boolean hasTemplate() {
        return false;
    }
}
