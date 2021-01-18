package edu.nyu.classes.seats.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Comparator;
import java.text.DateFormat;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.time.api.Time;

import java.util.stream.Collectors;
import java.util.Collections;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.net.URL;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentTypeImageService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;


import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.authz.api.Member;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class SeatAssignmentHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        JSONObject result = new JSONObject();

        String siteId = (String)context.get("siteId");
        DBConnection db = (DBConnection)context.get("db");

        RequestParams p = new RequestParams(request);

        boolean isInstructor = (boolean)context.get("hasSiteUpd");

        String sectionId = p.getString("sectionId", null);
        if (sectionId == null) {
            throw new RuntimeException("Need argument: sectionId");
        }

        String groupId = p.getString("groupId", null);
        if (groupId == null) {
            throw new RuntimeException("Need argument: groupId");
        }

        String meetingId = p.getString("meetingId", null);
        if (meetingId == null) {
            throw new RuntimeException("Need argument: meetingId");
        }

        String netid = p.getString("netid", null);
        if (netid == null) {
            throw new RuntimeException("Need argument: netid");
        }

        SeatSection seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId).get();
        Meeting meeting = seatSection.fetchGroup(groupId).get().getOrCreateMeeting(meetingId);

        String seat = p.getString("seat", null);
        String currentSeat = p.getString("currentSeat", null);

        SeatAssignment seatAssignment = new SeatAssignment(null, netid, seat, 0, meeting);

        // Force student to their own netid
        if (!isInstructor) {
            User currentUser = UserDirectoryService.getCurrentUser();
            netid = currentUser.getEid();
        }

        if (isInstructor && seatAssignment.seat == null) {
            SeatsStorage.clearSeat(db, seatAssignment);
        } else if (seatAssignment.seat != null) {
            SeatsStorage.SetSeatResult seatResult = SeatsStorage.setSeat(db, seatAssignment, currentSeat, isInstructor);
            if (!SeatsStorage.SetSeatResult.OK.equals(seatResult)) {
                result.put("error", true);
                if (SeatsStorage.SetSeatResult.SEAT_TAKEN.equals(seatResult)) {
                    result.put("error_code", isInstructor ? "SEAT_TAKEN_INSTRUCTOR" : "SEAT_TAKEN");
                } else {
                    result.put("error_code", seatResult.toString());
                }
            }
        } else {
            result.put("error", true);
            result.put("error_code", "SEAT_REQUIRED");
        }

        try {
            response.getWriter().write(result.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getContentType() {
        return "text/json";
    }

    @Override
    public boolean hasTemplate() {
        return false;
    }

    @Override
    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public Errors getErrors() {
        return null;
    }

    public boolean hasRedirect() {
        return false;
    }

    public String getRedirect() {
        return "";
    }

    @Override
    public boolean isSiteUpdRequired() {
        return false;
    }
}


