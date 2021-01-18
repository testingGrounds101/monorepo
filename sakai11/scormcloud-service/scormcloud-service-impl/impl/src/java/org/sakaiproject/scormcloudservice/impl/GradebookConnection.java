package org.sakaiproject.scormcloudservice.impl;

import org.sakaiproject.scormcloudservice.api.ScormException;
import org.sakaiproject.scormcloudservice.api.ScormRegistrationNotFoundException;
import org.sakaiproject.service.gradebook.shared.ConflictingAssignmentNameException;
import org.sakaiproject.service.gradebook.shared.GradebookExternalAssessmentService;
import org.sakaiproject.service.gradebook.shared.AssessmentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;


public class GradebookConnection {

    private ScormServiceStore store;
    private GradebookExternalAssessmentService gradebookService;

    private static final Logger LOG = LoggerFactory.getLogger(GradebookConnection.class);
    private static int MAX_TITLE_ATTEMPTS = 100;


    public GradebookConnection(ScormServiceStore store) {
        this.store = store;

        gradebookService = (GradebookExternalAssessmentService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.service.gradebook.GradebookExternalAssessmentService");
    }


    public void sendScores(String courseId, Map<String, Double> registrationToScore) throws ScormException {
        ScormCourse course = store.getCourseForId(courseId);

        if (course == null) {
            LOG.warn("No course could be found for ID {}", courseId);
            return;
        }

        Map<String, String> registrationToUser = store.getUsersForRegistrations(registrationToScore.keySet());

        Map<String, String> userScores = new HashMap<>(registrationToScore.size());

        for (String registrationId : registrationToScore.keySet()) {
            String userId = registrationToUser.get(registrationId);

            if (userId != null) {
                userScores.put(userId, registrationToScore.get(registrationId).toString());
            }
        }

        createAssessmentIfMissing(course.getSiteId(), course.getId(), course.getTitle());
        gradebookService.updateExternalAssessmentScoresString(course.getSiteId(), course.getId(), userScores);
    }


    public void removeScore(String registrationId) throws ScormException, ScormRegistrationNotFoundException {
        String userId = store.getUserForRegistration(registrationId);
        ScormCourse course = store.getCourseForRegistration(registrationId);

        if (userId == null || course == null) {
            throw new ScormRegistrationNotFoundException(registrationId);
        }

        createAssessmentIfMissing(course.getSiteId(), course.getId(), course.getTitle());
        gradebookService.updateExternalAssessmentScore(course.getSiteId(), course.getId(), userId, null);
    }


    public void createAssessmentIfMissing(String siteId, String assessmentId, String title)
            throws ScormException {
        boolean succeeded = false;

        for (int attempt = 1; attempt < MAX_TITLE_ATTEMPTS; attempt++) {
            try {
                createAssessmentIfMissing(siteId, assessmentId, title, attempt);
                succeeded = true;
                break;
            } catch (ConflictingAssignmentNameException e) {
            }
        }

        if (!succeeded) {
            throw new ScormException("Couldn't create an assessment with title: " + title + " for site " + siteId);
        }
    }

    public void delete(String siteId, String assessmentId) throws ScormException {
        try {
            gradebookService.removeExternalAssessment(siteId, assessmentId);
        } catch (AssessmentNotFoundException e) {
            // Feh.  Entirely possible!
        }
    }

    private void createAssessmentIfMissing(String siteId, String assessmentId, String title, int attempt) {
        if (!gradebookService.isGradebookDefined(siteId)) {
            LOG.error("Can't update gradebook for site {} because there's no gradebook", siteId);
            return;
        }

        if (attempt > 1) {
            title = (title + " (" + attempt + ")");
        }

        if (gradebookService.isExternalAssignmentDefined(siteId, assessmentId)) {
            // Just update title
            gradebookService.updateExternalAssessment(siteId, assessmentId, null, title, (double) 100.0, null);
        } else {
            gradebookService.addExternalAssessment(siteId, assessmentId, null, title, (double) 100.0, null, "SCORM Cloud Service");
        }
    }
}

