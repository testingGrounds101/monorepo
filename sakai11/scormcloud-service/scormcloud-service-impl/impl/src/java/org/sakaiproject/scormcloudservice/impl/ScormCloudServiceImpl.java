package org.sakaiproject.scormcloudservice.impl;

import com.rusticisoftware.hostedengine.client.CourseService;
import com.rusticisoftware.hostedengine.client.ReportingService;
import com.rusticisoftware.hostedengine.client.Configuration;
import com.rusticisoftware.hostedengine.client.datatypes.Enums;
import com.rusticisoftware.hostedengine.client.RegistrationService;
import com.rusticisoftware.hostedengine.client.ScormCloud;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.scormcloudservice.api.ScormCloudService;
import org.sakaiproject.scormcloudservice.api.ScormUploadStatus;
import org.sakaiproject.scormcloudservice.api.ScormException;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ScormCloudServiceImpl implements ScormCloudService {

    private static final Logger LOG = LoggerFactory.getLogger(ScormCloudServiceImpl.class);

    public String getScormPlayerUrl(String siteId, String externalId, String backurl) throws ScormException {
        User currentUser = UserDirectoryService.getCurrentUser();

        String firstName = currentUser.getFirstName();
        if (firstName == null || "".equals(firstName)) {
            firstName = currentUser.getEid();
        }

        String lastName = currentUser.getLastName();
        if (lastName == null || "".equals(lastName)) {
            lastName = currentUser.getEid();
        }

        String registrationId = addRegistration(siteId, externalId, currentUser.getId(), firstName, lastName);

        try {
            RegistrationService registration = ScormCloud.getRegistrationService();
            return registration.GetLaunchUrl(registrationId, backurl);
        } catch (Exception e) {
            throw new ScormException("Couldn't determine launch URL", e);
        }
    }

    public boolean wasLaunchedByCurrentUser(String siteId, String externalId) {
        User currentUser = UserDirectoryService.getCurrentUser();
        ScormServiceStore store = new ScormServiceStore();

        try {
            return store.hasRegistration(siteId, externalId, currentUser.getId()) != null;
        } catch (ScormException e) {
        }

        return false;
    }

    public void addCourse(String siteId, String externalId, String resourceId, String title, boolean graded) throws ScormException {
        ScormServiceStore store = new ScormServiceStore();
        store.addCourse(siteId, externalId, resourceId, title, graded);
        if (graded) {
            createGradebook(store, siteId, externalId, title);
        }
    }

    public void updateCourse(String siteId, String externalId, String title, boolean graded) throws ScormException {
        ScormServiceStore store = new ScormServiceStore();
        store.updateCourse(siteId, externalId, title, graded);

        if (graded) {
            createGradebook(store, siteId, externalId, title);
        }
    }

    public void markAsDeleted(String siteId, String externalId) throws ScormException {
        ScormServiceStore store = new ScormServiceStore();
        String courseId = store.findCourseOrJobId(siteId, externalId);

        store.markAsDeleted(courseId);

        GradebookConnection gradebook = new GradebookConnection(store);

        if (courseId != null) {
            gradebook.delete(siteId, courseId);
        } else {
            LOG.error("Couldn't find course for site {} item {}", siteId, externalId);
        }
    }


    private void createGradebook(ScormServiceStore store, String siteId, String externalId, String title)
            throws ScormException {
        GradebookConnection gradebook = new GradebookConnection(store);

        String courseId = store.findCourseOrJobId(siteId, externalId);

        if (courseId != null) {
            gradebook.createAssessmentIfMissing(siteId, courseId, title);
        } else {
            LOG.error("Couldn't find course for site {} item {}", siteId, externalId);
        }
    }

    public boolean isCourseReady(String siteId, String externalId) {
        ScormServiceStore store = new ScormServiceStore();
        try {
            return (store.findCourseId(siteId, externalId) != null);
        } catch (ScormException e) {
            return false;
        }
    }

    // Return true if a registration was added.  False if we already had it.
    public String addRegistration(String siteId, String externalId, String userId, String firstName, String lastName)
            throws ScormException {

        ScormServiceStore store = new ScormServiceStore();
        String registrationId = null;

        if ((registrationId = store.hasRegistration(siteId, externalId, userId)) != null) {
            return registrationId;
        }

        try {
            RegistrationService registration = ScormCloud.getRegistrationService();
            registrationId = store.mintId();
            String courseId = store.findCourseId(siteId, externalId);

            registration.CreateRegistration(registrationId, courseId, userId, firstName, lastName);
            store.recordRegistration(registrationId, courseId, userId);

            return registrationId;
        } catch (Exception e) {
            throw new ScormException("Failure while creating registration", e);
        }
    }

    public void init() {
        Configuration config = getConfiguration();
        ScormCloud.setConfiguration(config);
    }

    public void destroy() {
    }


    public Configuration getConfiguration() {
        String appId = ServerConfigurationService.getString("scormcloudservice.appid", "");
        String secret = ServerConfigurationService.getString("scormcloudservice.secret", "");

        if (appId.isEmpty() || secret.isEmpty()) {
            throw new RuntimeException("You need to specify scormcloudservice.appid and scormcloudservice.secret");
        }

        return new Configuration(ServerConfigurationService.getString("scormcloudservice.url", "https://cloud.scorm.com"),
                ServerConfigurationService.getString("scormcloudservice.appid", ""),
                ServerConfigurationService.getString("scormcloudservice.secret", ""),
                "sakai.scormcloudservice");
    }


    public void markCourseForGradeSync(String siteId, String externalId) throws ScormException {
        ScormServiceStore store = new ScormServiceStore();
        store.markCourseForGradeSync(siteId, externalId);
    }

    public void runImportProcessingRound() throws ScormException {
        new ScormCloudJobProcessor().run();
    }


    public void runGradeSyncRound() throws ScormException {
        new GradeSyncProcessor().run();
    }


    public String getPreviewUrl(String siteId, String externalId) throws ScormException {
        try {
            ScormServiceStore store = new ScormServiceStore();
            CourseService service = ScormCloud.getCourseService();
            return service.GetPreviewUrl(store.findCourseId(siteId, externalId));
        } catch (Exception e) {
            throw new ScormException("Couldn't determine preview URL", e);
        }
    }


    public String getReportUrl(String siteId, String externalId) throws ScormException {
        try {
            ScormServiceStore store = new ScormServiceStore();
            ReportingService service = ScormCloud.getReportingService();

            String auth = service.GetReportageAuth(Enums.ReportageNavPermission.DOWNONLY, false);

            String baseUrl = ServerConfigurationService.getString("scormcloudservice.reportage-base-url", "https://cloud.scorm.com/Reportage/reportage.php");
            String reportUrl = baseUrl + "?appId=" + ScormCloud.getAppId() + "&courseId=" + store.findCourseId(siteId, externalId) + "&dateRangeType=p12m";

            return service.GetReportUrl(auth, reportUrl);
        } catch (Exception e) {
            throw new ScormException("Couldn't determine report URL", e);
        }
    }


    public String getConsoleUrl(String siteId, String externalId) throws ScormException {
        ScormServiceStore store = new ScormServiceStore();

        String baseUrl = ServerConfigurationService.getString("scormcloudservice.console-base-url", "https://cloud.scorm.com/sc/user/Course");
        String consoleUrl = baseUrl + "?appId=" + ScormCloud.getAppId() + "&courseId=" + store.findCourseId(siteId, externalId);

        return consoleUrl;
    }


    public ScormUploadStatus getUploadStatus(String siteId, String externalId) {
        ScormServiceStore store = new ScormServiceStore();
        return store.getUploadStatus(siteId, externalId);
    }

}
