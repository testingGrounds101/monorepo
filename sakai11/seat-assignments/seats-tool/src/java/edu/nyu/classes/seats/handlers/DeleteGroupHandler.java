package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.SeatAssignment;
import edu.nyu.classes.seats.models.SeatGroup;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SeatsStorage;
import edu.nyu.classes.seats.storage.db.DBConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

public class DeleteGroupHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        RequestParams p = new RequestParams(request);
        String sectionId = p.getString("sectionId", null);
        String groupId = p.getString("groupId", null);

        Locks.lockSiteForUpdate(siteId);
        try {
            SeatSection seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId).get();
            Optional<SeatGroup> group = seatSection.listGroups().stream().filter(g -> g.id.equals(groupId)).findFirst();
            if (group.isPresent()) {
                for (Meeting meeting : group.get().listMeetings()) {
                    SeatsStorage.deleteMeeting(db, meeting);
                }
                SeatsStorage.deleteGroup(db, group.get());
            }
        } finally {
            Locks.unlockSiteForUpdate(siteId);
        }

        try {
            response.getWriter().write("{}");
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
}


