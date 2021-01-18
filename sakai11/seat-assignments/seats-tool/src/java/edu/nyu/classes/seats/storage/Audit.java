package edu.nyu.classes.seats.storage;

import edu.nyu.classes.seats.storage.db.*;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.*;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONObject;

import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;


public class Audit {
    private static AtomicLong sequence = new AtomicLong(0);

    public enum AuditEvents {
        SEAT_CLEARED,
        SEAT_ASSIGNED,
        MEETING_DELETED,
        GROUP_DELETED,
        GROUP_CREATED,
        MEETING_CREATED,
        MEMBER_ADDED,
        MEMBER_DELETED,
        GROUP_DESCRIPTION_CHANGED,
    }

    private static String[] COLUMNS = new String[] {
        "netid",
        "group_id",
        "group_name",
        "section_id",
        "meeting_id",
        "meeting_location",
        "primary_stem_name",
        "seat",
    };

    private static String generateId(String uuid, long timestamp, long sequence) {
        return String.format("%d_%016d_%s", timestamp, sequence, uuid);
    }

    public static void insert(DBConnection db, AuditEvents event, JSONObject json) throws SQLException {
        long timestamp = System.currentTimeMillis();

        User currentUser = UserDirectoryService.getCurrentUser();
        String loggedInUser = (currentUser != null && currentUser.getEid() != null) ? currentUser.getEid() : null;

        String columns = Arrays.stream(COLUMNS).collect(Collectors.joining(", "));
        String placeholders = Arrays.stream(COLUMNS).map(_c -> "?").collect(Collectors.joining(", "));

        List<String> extraValues = Arrays.stream(COLUMNS).map((c) -> (String)json.get(c)).collect(Collectors.toList());

        db.run(String.format("insert into seat_audit (id, timestamp_ms, event_code, json, logged_in_user, %s) values (?, ?, ?, ?, ?, %s)",
                             columns, placeholders))
            .param(generateId(db.uuid(), timestamp, sequence.incrementAndGet()))
            .param(timestamp)
            .param(event.toString())
            .param(json.toString())
            .param(loggedInUser)
            .stringParams(extraValues)
            .executeUpdate();
    }
}
