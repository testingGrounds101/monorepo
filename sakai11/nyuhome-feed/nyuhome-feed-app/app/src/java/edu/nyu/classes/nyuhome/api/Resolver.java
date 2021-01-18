package edu.nyu.classes.nyuhome.api;

import java.util.Map;

public interface Resolver {
    public void addSite(String siteId);
    public void addUser(String userId);

    public Map<String, Map<String, String>> toMap();
}
