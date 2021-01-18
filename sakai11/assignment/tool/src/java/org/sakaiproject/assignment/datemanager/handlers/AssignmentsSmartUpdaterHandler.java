package org.sakaiproject.assignment.datemanager.handlers;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.cover.PreferencesService;
import org.sakaiproject.util.ResourceLoader;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;

public class AssignmentsSmartUpdaterHandler implements Handler {
    private String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            RequestParams p = new RequestParams(request);

            String browserTimeZoneStr = p.getString("browser_zone", "");

            String previousEarliestDate = p.getString("old_earliest", "");
            String newEarliestDate = p.getString("new_earliest", "");
            String substitutionsJSON = p.getString("substitutions", "[]");
            String assignmentsJSON = p.getString("assignments", "{}");

            if (previousEarliestDate.isEmpty() || newEarliestDate.isEmpty()) {
                response.getWriter().write(assignmentsJSON);
                return;
            }

            TimeZone userTimeZone = getUserTimeZone();
            TimeZone browserTimeZone = browserTimeZoneStr.isEmpty() ? userTimeZone : TimeZone.getTimeZone(browserTimeZoneStr);

            long days = calculateDifference(previousEarliestDate, newEarliestDate, userTimeZone);

            JSONArray assignments = (JSONArray)(new JSONParser().parse(assignmentsJSON));
            JSONArray substitutions = (JSONArray)(new JSONParser().parse(substitutionsJSON));

            updateAssignments(assignments, substitutions, days, userTimeZone, browserTimeZone);

            response.getWriter().write(assignments.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateAssignments(JSONArray assignments, JSONArray substitutions, long days, TimeZone userTimeZone, TimeZone browserTimeZone) {
        for (int i = 0; i < assignments.size(); i++) {
            JSONObject assignment = (JSONObject) assignments.get(i);

            for (String property : new String[] { "open_date", "due_date", "accept_until" }) {
                Instant time = parseIncomingTimestamp((String)assignment.get(property),
                                                      userTimeZone);

                if (time.toEpochMilli() == 0) {
                    continue;
                }

                ZonedDateTime adjustedLocal = ZonedDateTime.ofInstant(time, userTimeZone.toZoneId());
                adjustedLocal = adjustedLocal.plusDays(days);

                DayOfWeek targetDay = getTargetDayOfWeek(substitutions, adjustedLocal.getDayOfWeek());

                while (!adjustedLocal.getDayOfWeek().equals(targetDay)) {
                    adjustedLocal = adjustedLocal.plusDays(1);
                }

                Instant adjusted = Instant.from(adjustedLocal);

                // Further adjustment for any difference between the user's
                // timezone (in Sakai) and the browser's timezone.  Usually they
                // should be the same, but if the user doesn't follow the
                // timezone warning they might differ.
                long adjustmentAdjustment = userTimeZone.getOffset(adjusted.toEpochMilli()) - browserTimeZone.getOffset(adjusted.toEpochMilli());

                assignment.put(property + "_adjusted", adjusted.toEpochMilli() + adjustmentAdjustment);
            }
        }
    }

    private DayOfWeek getTargetDayOfWeek(JSONArray substitutions, DayOfWeek currentDayOfWeek) {
        for (int i = 0; i < substitutions.size(); i++) {
            JSONObject substitution = (JSONObject) substitutions.get(i);

            DayOfWeek substitutionFrom = DayOfWeek.valueOf(((String)substitution.get("from")).toUpperCase(Locale.ROOT));
            DayOfWeek substitutionTo = DayOfWeek.valueOf(((String)substitution.get("to")).toUpperCase(Locale.ROOT));

            if (currentDayOfWeek.equals(substitutionFrom)) {
                return substitutionTo;
            }
        }

        return currentDayOfWeek;
    }

    private long calculateDifference(String previousEarliestDate, String newEarliestDate, TimeZone userTimeZone) {
        Instant previousEarliest = parseIncomingTimestamp(previousEarliestDate, userTimeZone);
        Instant newEarliest = parseIncomingTimestamp(newEarliestDate, userTimeZone);

        return Duration.between(previousEarliest.truncatedTo(ChronoUnit.DAYS),
                                newEarliest.truncatedTo(ChronoUnit.DAYS))
            .toDays();
    }

    private Instant parseIncomingTimestamp(String timestamp, TimeZone userTimeZone) {
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(userTimeZone.toZoneId())
                .parse(timestamp, Instant::from);
        } catch (DateTimeParseException e) {
            throw new RuntimeException(e);
        }
    }

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
