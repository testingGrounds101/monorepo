package org.sakaiproject.scormcloudservice.api;

public interface ScormCloudService {
    public String getScormPlayerUrl(String siteId, String externalId, String backurl) throws ScormException;

    public String getPreviewUrl(String siteId, String externalId) throws ScormException;

    public String getReportUrl(String siteId, String externalId) throws ScormException;

    public String getConsoleUrl(String siteId, String externalId) throws ScormException;

    public void addCourse(String siteId, String externalId, String resourceId, String title, boolean graded) throws ScormException;

    public void updateCourse(String siteId, String externalId, String title, boolean graded) throws ScormException;

    public void markAsDeleted(String siteId, String externalId) throws ScormException;

    public String addRegistration(String siteId, String externalId, String userId, String firstName, String lastName) throws ScormException;

    public void runImportProcessingRound() throws ScormException;

    public boolean isCourseReady(String siteId, String externalId);

    public boolean wasLaunchedByCurrentUser(String siteId, String externalId);

    public void runGradeSyncRound() throws ScormException;

    public void markCourseForGradeSync(String siteId, String externalId) throws ScormException;

    public ScormUploadStatus getUploadStatus(String siteId, String externalId);
}
