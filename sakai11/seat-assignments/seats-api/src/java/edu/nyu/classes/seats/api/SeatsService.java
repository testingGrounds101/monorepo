package edu.nyu.classes.seats.api;

import java.util.List;

public interface SeatsService {
    void markSitesForSync(String ...siteId);
    void markSectionsForSync(List<String> sectionEids);
}
