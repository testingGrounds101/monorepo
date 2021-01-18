package edu.nyu.classes.seats.models;

import java.util.*;

public class SeatAssignment {
    public String id;
    public String netid;
    public String seat;
    public Meeting meeting;
    public long editableUntil;

    private String normalizeSeat(String seat) {
        if (seat == null) {
            return null;
        } else {
            String s = seat.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");

            if (s.isEmpty()) {
                return null;
            } else {
                return s;
            }
        }
    }

    public SeatAssignment(String id, String netid, String seat, long editableUntil, Meeting meeting) {
        this.id = id;
        this.netid = netid;
        this.seat = normalizeSeat(seat);
        this.meeting = meeting;
        this.editableUntil = editableUntil;
    }
}

