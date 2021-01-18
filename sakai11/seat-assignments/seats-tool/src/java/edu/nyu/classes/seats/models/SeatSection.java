package edu.nyu.classes.seats.models;

import java.util.HashMap;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;

public class SeatSection {
    private Map<String, SeatGroup> groups = new HashMap<>();

    public String id;
    public String siteId;
    public boolean provisioned;
    public boolean hasSplit;
    public String name;
    public String shortName;
    public String primaryStemName;

    public SeatSection(String id, String siteId, boolean provisioned, boolean hasSplit, String primaryStemName) {
        this.id = id;
        this.siteId = siteId;
        this.provisioned = provisioned;
        this.hasSplit = hasSplit;
        this.primaryStemName = primaryStemName;
    }

    public Collection<String> groupIds() {
        return groups.keySet();
    }

    public void addGroup(String id, String name, String description, String sakaiGroupId) {
        groups.put(id, new SeatGroup(id, name, description, sakaiGroupId, this));
    }

    public Optional<SeatGroup> fetchGroup(String id) {
        return Optional.of(groups.get(id));
    }

    public Collection<SeatGroup> listGroups() {
        return groups.values();
    }
}
