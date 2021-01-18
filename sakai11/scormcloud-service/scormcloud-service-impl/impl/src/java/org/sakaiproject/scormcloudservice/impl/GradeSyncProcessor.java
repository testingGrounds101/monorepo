package org.sakaiproject.scormcloudservice.impl;

import com.rusticisoftware.hostedengine.client.RegistrationService;
import com.rusticisoftware.hostedengine.client.ScormCloud;
import com.rusticisoftware.hostedengine.client.datatypes.RegistrationData;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.scormcloudservice.api.ScormException;
import org.sakaiproject.scormcloudservice.api.ScormRegistrationNotFoundException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;

class GradeSyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GradeSyncProcessor.class);

    private static final String SYNC_USER = "admin";

    final int MAX_SCORM_GRADESYNC_THREADS = ServerConfigurationService.getInt("scormcloudservice.max-gradesync-thread-count", 5);

    final int TIMEOUT_SECONDS = 300;

    private ScormServiceStore store;

    public void run() throws ScormException {
        ScormServiceStore store = new ScormServiceStore();

        try {
            Date startTime = new Date();
            Date lastSyncTime = store.getLastSyncTime();

            Set<String> courses = getScormCoursesChangedSince(lastSyncTime);
            courses.addAll(store.getCoursesNeedingSync());

            if (!courses.isEmpty()) {
                syncCourses(courses);
            }
            store.setLastSyncTime(startTime);
        } catch (ScormException e) {
            LOG.error("Failure while syncing SCORM grades", e);
        }
    }


    private Set<String> getScormCoursesChangedSince(Date since) {
        Set<String> result = new HashSet<String>();

        try {
            RegistrationService registrationService = ScormCloud.getRegistrationService();
            List<RegistrationData> registrationList = registrationService.GetRegistrationList(null, null, null, null, since, null);

            for (RegistrationData registration : registrationList) {
                result.add(registration.getCourseId());
            }
        } catch (Exception e) {
            LOG.error("Failure while getting list of updated registrations", e);
        }

        return result;
    }


    private void syncCourses(final Collection<String> courses) throws ScormException {
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(courses.size(), MAX_SCORM_GRADESYNC_THREADS));
        try {
            List<Future<Void>> results = new ArrayList<Future<Void>>();

            // Callable?
            for (final String courseId : courses) {
                Future<Void> result = workers.submit(new Callable() {
                    public Void call() {
                        Thread.currentThread().setName("ScormCloudService-GradeSync-" + courseId);

                        SessionManager sessionManager = (SessionManager) ComponentManager.get("org.sakaiproject.tool.api.SessionManager");
                        Session s = sessionManager.startSession();
                        s.setActive();
                        s.setUserId(SYNC_USER);
                        s.setUserEid(SYNC_USER);
                        sessionManager.setCurrentSession(s);

                        try {
                            syncCourse(courseId, new ScormServiceStore());
                        } catch (ScormException e) {
                            throw new RuntimeException(e);
                        }

                        return null;
                    }
                });

                results.add(result);
            }

            Throwable failure = null;
            for (Future<Void> worker : results) {
                try {
                    worker.get();
                } catch (ExecutionException e) {
                    failure = e;
                } catch (InterruptedException e) {
                    failure = e;
                }
            }

            if (failure != null) {
                throw new ScormException("Failure while syncing courses", failure);
            }

        } finally {
            workers.shutdown();
            try {
                while (!workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
            }
        }
    }


    private class ScormScore {

        boolean resetRequest = false;
        boolean unknownScore = false;
        boolean invalidScore = false;
        String rawScore;
        double score;

        public ScormScore(String s) {
            rawScore = s;

            if (s.equals("unknown")) {
                // A score of 'unknown' in an otherwise completed course
                // actually means zero.  This is distinct from 'unknown' as we
                // use it here, which really means 'unparseable'.
                unknownScore = true;
            } else if (s.equals("reset")) {
                score = 0.0;
                resetRequest = true;
            } else {
                try {
                    this.score = Double.valueOf(s);
                } catch (NumberFormatException e) {
                    invalidScore = true;
                }
            }
        }

        public double getScore() {
            if (resetRequest || unknownScore) {
                throw new IllegalStateException("Can't fetch a score from an unknown/reset request");
            }

            return score;
        }

        public boolean isReset() {
            return resetRequest;
        }

        public boolean isUnknown() {
            return unknownScore;
        }

        public boolean isInvalid() {
            return invalidScore;
        }

        public String getRawScore() {
            return rawScore;
        }
    }


    private ScormScore extractScore(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        Document parsed = docBuilder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//complete");

        // if (!"complete".equals((String) expr.evaluate(parsed, XPathConstants.STRING))) {
        //     return new ScormScore("reset");
        // }

        xpath = xPathfactory.newXPath();
        expr = xpath.compile("//score");

        String value = (String) expr.evaluate(parsed, XPathConstants.STRING);

        return new ScormScore(value);
    }


    private void syncCourse(final String courseId, final ScormServiceStore store)
        throws ScormException {
        LOG.debug("Syncing SCORM courseId: " + courseId);

        GradebookConnection gradebook = new GradebookConnection(store);
        ScormCourse course = store.getCourseForId(courseId);

        if (course == null) {
          LOG.info("Received request to sync unknown course: " + courseId + ".  Skipped!");
          return;
        }

        if (course.isDeleted()) {
          LOG.info("Skipping sync for deleted course: " + courseId);
          return;
        }

        try {
            RegistrationService registrationService = ScormCloud.getRegistrationService();

            List<RegistrationData> registrationList = registrationService.GetRegistrationListResults(courseId, null, null, null, null, null, null);

            LOG.info(courseId + ": Found " + registrationList.size() + " registrations to sync");

            Map<String, Double> batch = new HashMap<>();

            for (RegistrationData registration : registrationList) {
                String registrationId = registration.getRegistrationId();
                String registrationResult = registration.getResultsData();

                ScormScore scoreFromResult = extractScore(registrationResult);

                if (scoreFromResult.isReset() || scoreFromResult.isUnknown()) {
                    if (scoreFromResult.isReset()) {
                        LOG.debug("Processing a reset for registration: " + registrationId);
                    } else {
                        LOG.debug("Score for registration " + registrationId + " was marked as 'unknown'");
                    }

                    store.removeScore(registrationId);
                    if (course.getGraded()) {
                        try {
                            gradebook.removeScore(registrationId);
                        } catch (ScormRegistrationNotFoundException e) {
                            if (scoreFromResult.isUnknown()) {
                                // This can happen when a registration was created in SCORM Cloud
                                // but never stored locally.  For example, if the API call to create
                                // the registration times out due to network issues, we'll catch an
                                // exception locally and discard the registration, but it lives on
                                // in SCORM Cloud.
                                //
                                // As long as the registration doesn't have a score recorded, we can
                                // safely ignore these (since the student would have created a
                                // second registration when they retried the launch).
                            } else {
                                throw e;
                            }
                        }
                    }
                } else if (scoreFromResult.isInvalid()) {
                    LOG.error("Received an unparseable score from SCORM Cloud API for registration: " + registrationId +
                            " score was: " + scoreFromResult.getRawScore());
                } else {
                    LOG.debug("Recording score for registration: " + registrationId +
                            ": " + scoreFromResult.getRawScore());

                    batch.put(registrationId, scoreFromResult.getScore());
                }
            }

            store.recordScores(batch);
            if (course.getGraded()) {
                gradebook.sendScores(courseId, batch);
            }

        } catch (Exception e) {
            throw new ScormException("Failure when syncing grades for " + courseId, e);
        }
    }

}
