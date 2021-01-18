package org.sakaiproject.scormcloudservice.impl;


abstract class ScormCourseData {
    protected String uuid;
    protected String siteId;
    protected String externalId;
    protected String resourceId;
    protected String title;
    protected boolean graded;
    protected boolean deleted;

    public ScormCourseData(String uuid, String siteId, String externalId, String resourceId, String title, boolean graded, boolean deleted) {
        this.uuid = uuid;
        this.siteId = siteId;
        this.externalId = externalId;
        this.resourceId = resourceId;
        this.title = title;
        this.graded = graded;
        this.deleted = deleted;
    }

    public String getId() {
        return uuid;
    }

    public String getSiteId() {
        return siteId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getTitle() {
        return title;
    }

    public boolean getGraded() {
        return graded;
    }

    public boolean isDeleted() {
        return deleted;
    }
}

