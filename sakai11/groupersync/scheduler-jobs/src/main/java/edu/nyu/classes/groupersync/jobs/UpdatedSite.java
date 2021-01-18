package edu.nyu.classes.groupersync.jobs;

import java.util.Date;

class UpdatedSite {
    private final Date updateTime;
    private final String siteId;

    public UpdatedSite(String siteId, Date updateTime) {
        this.siteId = siteId;
        this.updateTime = updateTime;
    }

    public String getSiteId() {
        return siteId;
    }
}
