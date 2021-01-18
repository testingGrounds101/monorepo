package edu.nyu.classes.nyuhome.api;

import java.util.List;

public interface DataFeed {
    public List<DataFeedEntry> getUserData(QueryUser user, Resolver resolve, int maxAgeDays, int maxResults);
}
