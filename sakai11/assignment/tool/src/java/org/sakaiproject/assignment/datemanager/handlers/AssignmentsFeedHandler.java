package org.sakaiproject.assignment.datemanager.handlers;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.cover.PreferencesService;
import org.sakaiproject.util.ResourceLoader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AssignmentsFeedHandler implements Handler {
    private String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            RequestParams p = new RequestParams(request);

            String siteId = (String)context.get("siteId");

            AssignmentService assignmentService = ComponentManager.get(AssignmentService.class);
            Collection<Assignment> assignments = assignmentService.getAssignmentsForContext(siteId);

            JSONObject result = new JSONObject();
            JSONArray jsonAssignments = new JSONArray();

            for (Assignment assignment : assignments) {
                JSONObject assobj = new JSONObject();
                assobj.put("id", assignment.getId());
                assobj.put("title", assignment.getTitle());
                assobj.put("due_date", formatDateToString(assignment.getDueDate()));
                assobj.put("open_date", formatDateToString(assignment.getOpenDate()));
                assobj.put("accept_until", formatDateToString(assignment.getCloseDate()));
                assobj.put("published", !assignment.getDraft());
                jsonAssignments.add(assobj);
            }

            result.put("timezone", getUserTimeZone().getID());
            result.put("assignments", jsonAssignments);

            response.getWriter().write(result.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String formatDateToString(Instant dateTime) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                 .withZone(getUserTimeZone().toZoneId())
                 .format(dateTime);
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
