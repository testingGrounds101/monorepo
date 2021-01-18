package edu.nyu.classes.seats.models;

import java.util.ArrayList;
import java.util.List;

public class Meeting {
    public String id;
    public String name;
    public SeatGroup group;

    private List<SeatAssignment> seatAssignments = new ArrayList<>();

    public Meeting(String id, SeatGroup group) {
        this.id = id;
        this.name = "MEETING " + id;
        this.group = group;
    }

    public void addSeatAssignment(String id, String netid, String seat, long editableUntil) {
        this.seatAssignments.add(new SeatAssignment(id, netid, seat, editableUntil, this));
    }

    public List<SeatAssignment> listSeatAssignments() {
        return this.seatAssignments;
    }
}
