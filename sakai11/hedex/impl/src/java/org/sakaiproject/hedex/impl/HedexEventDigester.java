package org.sakaiproject.hedex.impl;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.lang3.StringUtils;

import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.AssignmentConstants;
import org.sakaiproject.assignment.api.AssignmentServiceConstants;
import org.sakaiproject.assignment.api.AssignmentReferenceReckoner;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.assignment.api.model.AssignmentSubmission;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.presence.api.PresenceService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

import org.sakaiproject.hedex.api.model.AssignmentSubmissions;
import org.sakaiproject.hedex.api.model.CourseVisits;
import org.sakaiproject.hedex.api.model.SessionDuration;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts certain Sakai events into log entries in the database. Session
 * durations, course visits and assignment gradings are all handled.
 *
 * @author Adrian Fish <adrian.r.fish@gmail.com>
 */
@Slf4j
public class HedexEventDigester implements Observer {

    // When SAK-39995 is merged into 12.x and released, this should be replaced
    private final String PRESENCE_SUFFIX = "-presence";

    /**
     * Handled events. Events not in this list will just be cheaply skipped
     */
    private static final List<String> HANDLED_EVENTS
        = Arrays.asList(UsageSessionService.EVENT_LOGIN
                        , UsageSessionService.EVENT_LOGOUT
                        , AssignmentConstants.EVENT_GRADE_ASSIGNMENT_SUBMISSION
                        , AssignmentConstants.EVENT_SUBMIT_ASSIGNMENT_SUBMISSION
                        , PresenceService.EVENT_PRESENCE);

    @Setter
    private EventTrackingService eventTrackingService;

    @Setter
    private SecurityService securityService;

    @Setter
    private ServerConfigurationService serverConfigurationService;

    @Setter
    private SessionFactory sessionFactory;

    @Setter
    private AssignmentService assignmentService;

    @Setter
    private SiteService siteService;

    @Setter
    private TransactionTemplate transactionTemplate;

    private AtomicBoolean tomcatStarted = new AtomicBoolean(false);

    /**
     * Every hedex.site.update.interval minutes, this will be updated with the
     * site ids and agents of sites marked up with the hedex-agent property.
     */
    private Map<String, String> siteAgents = new ConcurrentHashMap<>();

    /**
     * Every hedex.site.update.interval minutes, this will be updated with the
     * user ids of members of Hedex sites, with the agents. If we have students
     * on courses from multiple agents, then this will have to be changed. It
     * assumes a 121 mapping between student and hedex-agent.
     */
    private Map<String, String> memberAgents = new ConcurrentHashMap<>();

    private ScheduledExecutorService siteIdRefresher;

    public void init() {

        log.debug("init()");

        if (serverConfigurationService.getBoolean("hedex.digester.enabled", true)) {
            waitForTomcatStartup();
            eventTrackingService.addLocalObserver(this);

            // Load the map with the agent keyed list of sites
            String hedexAgentProperty = serverConfigurationService.getString("hedex.agent.property", "hedex-agent");
            log.debug("hedex.agent.property: {}", hedexAgentProperty);
            int hedexSiteUpdateInterval = serverConfigurationService.getInt("hedex.site.update.interval", 30);
            log.debug("hedex.site.update.interval: {}", hedexSiteUpdateInterval);

            Map<String, String> desiredProps = new HashMap<>();
            desiredProps.put(hedexAgentProperty , "");
            siteIdRefresher = Executors.newSingleThreadScheduledExecutor();
            siteIdRefresher.scheduleAtFixedRate(() -> {
                    if (!tomcatStarted.get()) {
                        log.info("Skipping run of Hedex siteIdRefresher while Tomcat starts up.");
                        return;
                    }

                    log.debug("Refreshing agent caches ...");
                    siteAgents.clear();
                    memberAgents.clear();
                    List<Site> hedexSites
                        = siteService.getSites(SelectionType.ANY, null, null, desiredProps, SortType.NONE, null, false);
                    // Stash all the located sites in the siteAgents map
                    for (Site hedexSite : hedexSites) {
                        String agent = hedexSite.getProperties().getProperty(hedexAgentProperty);
                        siteAgents.put(hedexSite.getId(), agent);
                        // Now stash the member user ids for the Hedex sites. We use this for the session duration
                        // stuff, which is based on login events, which don't have a site id, obviously.
                        /*for (Member member : hedexSite.getMembers()) {
                            memberAgents.put(member.getUserId(), agent);
                        }*/
                        hedexSite.getMembers().forEach(m -> { memberAgents.put(m.getUserId(), agent); });
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("Site Agents:");
                        siteAgents.forEach((k,v) -> { log.debug("\t{}:{}", k, v); });
                        log.debug("Member Agents:");
                        memberAgents.forEach((k,v) -> { log.debug("\t{}:{}", k, v); });
                    }
                }, 0, hedexSiteUpdateInterval, TimeUnit.MINUTES);
        } else {
            log.info("HEDEX event digester not enabled on this server");
        }
    }


    private void waitForTomcatStartup() {
        Thread tomcatStartupMonitor = new Thread(() -> {
                Thread[] allThreads = new Thread[4096];
                while (true) {
                    int threadCount = Thread.enumerate(allThreads);

                    boolean startingUp = false;
                    for (int i = 0; i < threadCount; i++) {
                        if (allThreads[i].getName().indexOf("-startStop-") >= 0) {
                            startingUp = true;
                        }
                    }

                    if (!startingUp) {
                        break;
                    }

                    try {
                        log.info("Waiting for Tomcat to start up before enabling HedexEventDigester thread");
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {}
                }

                tomcatStarted.set(true);
                log.info("Tomcat started: HedexEventDigester now enabled.");
        });

        tomcatStartupMonitor.start();
    }

    public void destroy() {

		if (siteIdRefresher != null) {
			siteIdRefresher.shutdown(); // Disable new tasks from being submitted
			try {
				if (!siteIdRefresher.awaitTermination(60, TimeUnit.SECONDS)) {
					siteIdRefresher.shutdownNow(); // Cancel currently executing tasks
			   		if (!siteIdRefresher.awaitTermination(60, TimeUnit.SECONDS)) {
				   		log.error("siteIdRefresher did not terminate");
					}
		        }
			} catch (InterruptedException ie) {
			    siteIdRefresher.shutdownNow();
			    Thread.currentThread().interrupt();
			}
		}
    }

    public void update(Observable o, final Object arg) {

        if (arg instanceof Event) {
            Event event = (Event) arg;
            String eventName = event.getEvent();
            log.debug("Event '{}' ...", eventName);

            String testSiteId = event.getContext();
            if (testSiteId == null) testSiteId = "";
            final String siteAgent = siteAgents.get(testSiteId);

            final String eventUserId = event.getUserId();

            if (HANDLED_EVENTS.contains(eventName)  && !EventTrackingService.UNKNOWN_USER.equals(eventUserId)) {

                log.debug("Handling event '{}' ...", eventName);

                final String sessionId = event.getSessionId();
                final String reference = event.getResource();
                final String memberAgent = memberAgents.get(eventUserId);

                if (UsageSessionService.EVENT_LOGIN.equals(eventName) && memberAgent != null) {
                    transactionTemplate.execute(new TransactionCallbackWithoutResult() {

                        protected void doInTransactionWithoutResult(TransactionStatus status) {

                            SessionDuration sd = new SessionDuration();
                            sd.setUserId(eventUserId);
                            sd.setSessionId(sessionId);
                            sd.setStartTime(event.getEventTime());
                            sd.setAgent(memberAgent);
                            sessionFactory.getCurrentSession().persist(sd);
                        }
                    });
                } else if (UsageSessionService.EVENT_LOGOUT.equals(eventName) && memberAgent != null) {
                    transactionTemplate.execute(new TransactionCallbackWithoutResult() {

                        protected void doInTransactionWithoutResult(TransactionStatus status) {

                            List<SessionDuration> sessionDurations
                                = sessionFactory.getCurrentSession().createCriteria(SessionDuration.class)
                                    .add(Restrictions.eq("sessionId", sessionId)).list();
                            if (sessionDurations.size() == 1) {
                                SessionDuration sd = sessionDurations.get(0);
                                sd.setDuration(event.getEventTime().getTime() - sd.getStartTime().getTime());
                                sessionFactory.getCurrentSession().save(sd);
                            } else {
                                log.error("No SessionDuration for event sessionId: " + sessionId);
                            }
                        }
                    });
                } else if (AssignmentConstants.EVENT_SUBMIT_ASSIGNMENT_SUBMISSION.equals(eventName) && siteAgent != null) {
                    // We need to check for the fully formed submit event.
                    if (reference.contains("/")) {
                        AssignmentReferenceReckoner.AssignmentReference submissionReference
                            = AssignmentReferenceReckoner.reckoner().reference(reference).reckon();
                        String siteId = event.getContext();
                        String assignmentId = submissionReference.getContainer();
                        String submissionId = submissionReference.getId();
                        try {
                            Assignment assignment = assignmentService.getAssignment(assignmentId);

                            Assignment.GradeType gradeType = assignment.getTypeOfGrade();
                            // Lookup the current AssignmentSubmissions record. There should only
                            // be <= 1 for this user and assignment.
                            Session session = sessionFactory.getCurrentSession();
                            List<AssignmentSubmissions> assignmentSubmissionss
                                = session.createCriteria(AssignmentSubmissions.class)
                                    .add(Restrictions.eq("userId", eventUserId))
                                    .add(Restrictions.eq("assignmentId", assignmentId))
                                    .add(Restrictions.eq("submissionId", submissionId)).list();

                            assert assignmentSubmissionss.size() <= 1;

                            if (assignmentSubmissionss.size() <= 0) {
                                // No record yet. Create one.
                                AssignmentSubmissions as = new AssignmentSubmissions();
                                as.setUserId(eventUserId);
                                as.setAssignmentId(assignmentId);
                                as.setSubmissionId(submissionId);
                                as.setSiteId(siteId);
                                as.setTitle(assignment.getTitle());
                                as.setDueDate(Date.from(assignment.getDueDate()));
                                as.setNumSubmissions(1);
                                as.setAgent(siteAgent);

                                session.persist(as);
                            } else {
                                AssignmentSubmissions as = assignmentSubmissionss.get(0);
                                as.setNumSubmissions(as.getNumSubmissions() + 1);
                                session.update(as);
                            }
                        } catch (Exception e) {
                            log.error("Failed to in insert/update AssignmentSubmissions", e);
                        }
                    }
                } else if (AssignmentConstants.EVENT_GRADE_ASSIGNMENT_SUBMISSION.equals(eventName) && siteAgent != null) {
                    AssignmentReferenceReckoner.AssignmentReference submissionReference
                        = AssignmentReferenceReckoner.reckoner().reference(reference).reckon();

                    final String siteId = submissionReference.getContext();
                    final String assignmentId = submissionReference.getContainer();
                    final String submissionId = submissionReference.getId();

                    SecurityAdvisor sa = unlock(new String[] {AssignmentServiceConstants.SECURE_ACCESS_ASSIGNMENT_SUBMISSION
                                                    , AssignmentServiceConstants.SECURE_ACCESS_ASSIGNMENT
                                                    , AssignmentServiceConstants.SECURE_ADD_ASSIGNMENT_SUBMISSION});

                    try {
                        Session session = sessionFactory.getCurrentSession();
                        log.debug("Searching for record for assignment id {} and submission id {}"
                                    , assignmentId, submissionId);
                        final List<AssignmentSubmissions> assignmentSubmissionss
                            = session.createCriteria(AssignmentSubmissions.class)
                                .add(Restrictions.eq("assignmentId", assignmentId))
                                .add(Restrictions.eq("submissionId", submissionId)).list();

                        assert assignmentSubmissionss.size() == 1;

                        final Assignment assignment = assignmentService.getAssignment(assignmentId);
                        assert assignment != null;
                        final Assignment.GradeType gradeType = assignment.getTypeOfGrade();

                        if (assignmentSubmissionss.size() == 1) {
                            log.debug("One HEDEX submissions record found.");
                            final AssignmentSubmission submission = assignmentService.getSubmission(submissionId);
                            assert submission != null;
                            final String grade = submission.getGrade();
                            log.debug("GRADE: {}", grade);
                            assert grade != null;
                            if (grade != null) {
                                AssignmentSubmissions as = assignmentSubmissionss.get(0);
                                if (as.getFirstScore() == null) {
                                    log.debug("This is the first grading");
                                    // First time this submission has been graded
                                    as.setFirstScore(grade);
                                    as.setLastScore(grade);
                                    as.setNumGradings(1);
                                    if (gradeType.equals(Assignment.GradeType.SCORE_GRADE_TYPE)) {
                                        // This is a numeric grade, so we can do numeric stuff with it.
                                        try {
                                            int numericScore = Integer.parseInt(grade);
                                            as.setLowestScore(numericScore);
                                            as.setHighestScore(numericScore);
                                            as.setAverageScore((float)numericScore);
                                        } catch (NumberFormatException nfe) {
                                            log.error("Failed to set scores on graded submission "
                                                        + submissionId + " - NumberFormatException on " + grade);
                                        }
                                    }
                                } else {
                                    log.debug("This is not the first grading");
                                    as.setLastScore(grade);
                                    if (gradeType.equals(Assignment.GradeType.SCORE_GRADE_TYPE)) {
                                        // This is a numeric grade, so we can do numeric stuff with it.
                                        try {
                                            float currentAverageScore = as.getAverageScore();
                                            int numGradings = as.getNumGradings();
                                            int numericScore = Integer.parseInt(grade);
                                            float newAvg = (currentAverageScore*(float)numGradings + numericScore)/(numGradings+1);
                                            if (numericScore < as.getLowestScore()) as.setLowestScore(numericScore);
                                            else if (numericScore > as.getHighestScore()) as.setHighestScore(numericScore);
                                            as.setAverageScore(newAvg);
                                            as.setNumGradings(numGradings + 1);
                                        } catch (NumberFormatException nfe) {
                                            log.error("Failed to set scores on graded submission "
                                                        + submissionId + " - NumberFormatException on " + grade);
                                        }
                                    }
                                }
                                session.update(as);
                            } else {
                                log.error("Null grade set on submission " + submissionId
                                        + ". This is not right. We've had the event, we should have the grade.");
                            }
                        } else {
                            log.error("No submission for id: " + submissionId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to in insert/update AssignmentSubmissions", e);
                    } finally {
                        securityService.popAdvisor(sa);
                    }
                } else if (PresenceService.EVENT_PRESENCE.equals(eventName)) {
                    // Parse out the course id
                    String compoundId = reference.substring(reference.lastIndexOf("/") + 1);
                    String siteId = compoundId.substring(0, compoundId.indexOf(PRESENCE_SUFFIX));

                    if (siteId.startsWith("~")) {
                        // This is a user workspace
                        return;
                    }

                    String presenceSiteAgent = siteAgents.get(siteId);
                    if (presenceSiteAgent == null) {
                        // Not an agent site
                        return;
                    }

                    transactionTemplate.execute(new TransactionCallbackWithoutResult() {

                        protected void doInTransactionWithoutResult(TransactionStatus status) {

                            List<CourseVisits> courseVisitss
                                = sessionFactory.getCurrentSession().createCriteria(CourseVisits.class)
                                    .add(Restrictions.eq("userId", eventUserId))
                                    .add(Restrictions.eq("siteId", siteId)).list();

                            CourseVisits courseVisits = null;

                            assert courseVisitss.size() <= 1;

                            if (courseVisitss.size() <= 0) {
                                courseVisits = new CourseVisits();
                                courseVisits.setUserId(eventUserId);
                                courseVisits.setSiteId(siteId);
                                courseVisits.setNumVisits(1L);
                                courseVisits.setAgent(presenceSiteAgent);
                            } else {
                                courseVisits = courseVisitss.get(0);
                                courseVisits.setNumVisits(courseVisits.getNumVisits() + 1L);
                            }
                            courseVisits.setLatestVisit(event.getEventTime());
                            sessionFactory.getCurrentSession().saveOrUpdate(courseVisits);
                        }
                    });
                }
            }
        }
    }

    /**
     * Supply null to this and everything will be allowed. Supply
     * a list of functions and only they will be allowed.
     */
    private SecurityAdvisor unlock(final String[] functions) {

        SecurityAdvisor securityAdvisor = new SecurityAdvisor() {
                public SecurityAdvice isAllowed(String userId, String function, String reference) {

                    if (functions != null) {
                        if (Arrays.asList(functions).contains(function)) {
                            return SecurityAdvice.ALLOWED;
                        } else {
                            return SecurityAdvice.NOT_ALLOWED;
                        }
                    } else {
                        return SecurityAdvice.ALLOWED;
                    }
                }
            };
        securityService.pushAdvisor(securityAdvisor);
        return securityAdvisor;
    }
}
