package org.sakaiproject.hedex.tool.entityprovider;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.extension.ActionReturn;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.exception.EntityException;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.hedex.api.AssignmentRecord;
import org.sakaiproject.hedex.api.AssignmentRecords;
import org.sakaiproject.hedex.api.EngagementActivityRecord;
import org.sakaiproject.hedex.api.EngagementActivityRecords;
import org.sakaiproject.hedex.api.model.AssignmentSubmissions;
import org.sakaiproject.hedex.api.model.CourseVisits;
import org.sakaiproject.hedex.api.model.SessionDuration;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;

import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
public class HedexAPIEntityProvider extends AbstractEntityProvider
    implements AutoRegisterEntityProvider, ActionsExecutable, Describeable {

    private final static String REQUESTING_AGENT = "RequestingAgent";
    private final static String TERMS = "terms";
    private final static String START_DATE = "startDate";
    private final static String SEND_CHANGES_ONLY = "sendChangesOnly";
    private final static String LAST_RUN_DATE = "lastRunDate";
    private final static String INCLUDE_ALL_TERM_HISTORY = "lastRunDate";

    private String tenantId;
    private ObjectMapper objectMapper = new ObjectMapper();

    private final static DateFormat startDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private ServerConfigurationService serverConfigurationService;
    private SessionFactory sessionFactory;
    private SessionManager sessionManager;
    private SiteService siteService;

    public void init() {

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        tenantId = serverConfigurationService.getString("hedex.tenantId", "UNSPECIFIED");
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getEntityPrefix() {
		return "hedex";
	}

	@EntityCustomAction(action = "Get_Retention_Engagement_EngagementActivity", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getEngagementActivity(EntityReference reference, Map<String, Object> params) {


        String requestingAgent = getCheckedRequestingAgent(params, reference);
        checkSession(reference, params, requestingAgent);
        final String[] terms = getTerms(params);
        Date startDate = getValidatedDate((String) params.get(START_DATE));
        String sendChangesOnly = (String) params.get(SEND_CHANGES_ONLY);
        String lastRunDate = (String) params.get(LAST_RUN_DATE);
        String includeAllTermHistory = (String) params.get(INCLUDE_ALL_TERM_HISTORY);
        HashMap<String, String> userLookup = new HashMap<String, String>();

        Session session = sessionFactory.openSession();
        try {
            Criteria criteria = session.createCriteria(CourseVisits.class)
                .add(Restrictions.eq("agent", requestingAgent));
            if (startDate != null) {
                criteria.add(Restrictions.ge("latestVisit", startDate));
            }
            List<CourseVisits> courseVisitss = criteria.list();
            EngagementActivityRecords eaRecords = new EngagementActivityRecords();
            eaRecords.setTenantId(tenantId);
            List<EngagementActivityRecord> records = new ArrayList<>();
            for (CourseVisits cv : courseVisitss) {
                String personLmsId = cv.getUserId();
                String personSisId = getPersonSisId(personLmsId, userLookup);
                EngagementActivityRecord record = new EngagementActivityRecord();
                record.setPersonLmsId(personLmsId);
                record.setPersonSisId(personSisId);
                record.setLmsSectionId(cv.getSiteId());
                record.setLmsTotalLogin(cv.getNumVisits());
                record.setLmsLastAccessDate(cv.getLatestVisit().getTime());
                records.add(record);
            }

            eaRecords.setEngagementActivity(records);
            String json = objectMapper.writeValueAsString(eaRecords);
            return new ActionReturn(Formats.UTF_8, Formats.JSON_MIME_TYPE, json);
        } catch (Exception e) {
            log.error("Failed to get sessions.", e);
        } finally {
            session.close();
        }

        return null;
	}

    @EntityCustomAction(action = "Get_Retention_Engagement_SessionDurations", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getSessionDurations(EntityReference reference, Map<String, Object> params) {

        String requestingAgent = getCheckedRequestingAgent(params, reference);
        checkSession(reference, params, requestingAgent);
        final String[] terms = getTerms(params);
        Date startDate = getValidatedDate((String) params.get(START_DATE));
        String sendChangesOnly = (String) params.get(SEND_CHANGES_ONLY);
        String lastRunDate = (String) params.get(LAST_RUN_DATE);
        String includeAllTermHistory = (String) params.get(INCLUDE_ALL_TERM_HISTORY);
        HashMap<String, String> userLookup = new HashMap<String, String>();

        Session session = sessionFactory.openSession();
        try {
            Criteria criteria = session.createCriteria(SessionDuration.class)
                .add(Restrictions.eq("agent", requestingAgent));
            if (startDate != null) {
                criteria.add(Restrictions.ge("startTime", startDate));
            }
            List<SessionDuration> sessionDurations = criteria.list();
            EngagementActivityRecords eaRecords = new EngagementActivityRecords();
            eaRecords.setTenantId(tenantId);
            List<EngagementActivityRecord> engagementActivity = new ArrayList<>();
            Map<String, Long> totalTimes = new HashMap<>();
            Map<String, Integer> totalLogins = new HashMap<>();
            Map<String, Long> lastAccesses = new HashMap<>();
            Map<String, EngagementActivityRecord> records = new HashMap<>();
            for (SessionDuration sd : sessionDurations) {
                String personLmsId = sd.getUserId();
                String personSisId = getPersonSisId(personLmsId, userLookup);
                if (!totalTimes.containsKey(personLmsId)) {
                    totalTimes.put(personLmsId, 0L);
                }
                Long duration = sd.getDuration();
                if (duration != null) {
                    totalTimes.put(personLmsId, totalTimes.get(personLmsId) + duration);
                }
                if (!totalLogins.containsKey(personLmsId)) {
                    totalLogins.put(personLmsId, 0);
                }
                totalLogins.put(personLmsId, totalLogins.get(personLmsId) + 1);
                if (!lastAccesses.containsKey(personLmsId)) {
                    lastAccesses.put(personLmsId, 0L);
                }
                long storedAccessTime = lastAccesses.get(personLmsId);
                long sessionStartTime = sd.getStartTime().getTime();
                if (sessionStartTime > storedAccessTime) {
                    lastAccesses.put(personLmsId, sessionStartTime);
                }
                if (!records.containsKey(personLmsId)) {
                    EngagementActivityRecord record = new EngagementActivityRecord();
                    record.setPersonLmsId(personLmsId);
                    record.setPersonSisId(personSisId);
                    records.put(personLmsId, record);
                }
            }

            records.forEach((personLmsId,record) -> {
                record.setLmsTotalTime(totalTimes.get(personLmsId));
                record.setLmsTotalLogin(totalLogins.get(personLmsId));
                record.setLmsLastAccessDate(lastAccesses.get(personLmsId));
            });

            eaRecords.setEngagementActivity(new ArrayList<>(records.values()));
            String json = objectMapper.writeValueAsString(eaRecords);
            return new ActionReturn(Formats.UTF_8, Formats.JSON_MIME_TYPE, json);
        } catch (Exception e) {
            log.error("Failed to get sessions.", e);
        } finally {
            session.close();
        }

        return null;
	}

	@EntityCustomAction(action = "Get_Retention_Engagement_Assignments", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getAssignments(EntityReference reference, Map<String, Object> params) {

        String requestingAgent = getCheckedRequestingAgent(params, reference);
        checkSession(reference, params, requestingAgent);
        final String[] terms = getTerms(params);
        Date startDate = getValidatedDate((String) params.get(START_DATE));
        String sendChangesOnly = (String) params.get(SEND_CHANGES_ONLY);
        String lastRunDate = (String) params.get(LAST_RUN_DATE);
        String includeAllTermHistory = (String) params.get(INCLUDE_ALL_TERM_HISTORY);
        HashMap<String, String> userLookup = new HashMap<String, String>();

        Session session = sessionFactory.openSession();
        try {
            Criteria criteria = session.createCriteria(AssignmentSubmissions.class)
                .add(Restrictions.eq("agent", requestingAgent));
            if (startDate != null) {
                criteria.add(Restrictions.gt("dueDate", startDate));
            }
            List<AssignmentSubmissions> assignmentSubmissionss = criteria.list();
            List<AssignmentRecord> records = new ArrayList<>();
            if (assignmentSubmissionss.size() > 0) {
                for (AssignmentSubmissions submissions : assignmentSubmissionss) {
                    AssignmentRecord assignmentRecord = new AssignmentRecord();
                    assignmentRecord.setAssignmentLmsId(submissions.getAssignmentId());
                    String personLmsId = submissions.getUserId();
                    String personSisId = getPersonSisId(personLmsId, userLookup);
                    assignmentRecord.setPersonLmsId(personLmsId);
                    assignmentRecord.setPersonSisId(personSisId);
                    assignmentRecord.setAssignTitle(submissions.getTitle());
                    Date dueDate = submissions.getDueDate();
                    String assignDueDate = (dueDate==null) ? "" : dueDate.toString();
                    assignmentRecord.setAssignDueDate(assignDueDate);
                    assignmentRecord.setAssignScore(submissions.getLastScore());
                    Integer lowestScore = submissions.getLowestScore();
                    String assignLoScore = (lowestScore==null) ? "" : lowestScore.toString();
                    assignmentRecord.setAssignLoScore(assignLoScore);
                    Integer highestScore = submissions.getLowestScore();
                    String assignHiScore = (highestScore==null) ? "" : highestScore.toString();
                    assignmentRecord.setAssignHiScore(assignHiScore);
                    assignmentRecord.setAssignFirstAttmpt(submissions.getFirstScore());
                    assignmentRecord.setAssignLastAttmpt(submissions.getLastScore());
                    Float averageScore = submissions.getAverageScore();
                    String assignAvgAttmpt = (averageScore==null) ? "" : averageScore.toString();
                    assignmentRecord.setAssignAvgAttmpt(assignAvgAttmpt);
                    Integer numSubmissions = submissions.getNumSubmissions();
                    String assignNumAttempt = (numSubmissions==null) ? "" : numSubmissions.toString();
                    assignmentRecord.setAssignNumAttempt(assignNumAttempt);
                    records.add(assignmentRecord);
                }
            }
            AssignmentRecords assignmentRecords = new AssignmentRecords();
            assignmentRecords.setTenantId(tenantId);
            assignmentRecords.setAssignments(records);
            String json = objectMapper.writeValueAsString(assignmentRecords);
            return new ActionReturn(Formats.UTF_8, Formats.JSON_MIME_TYPE, json);
        } catch (Exception e) {
            log.error("Failed to serialise to JSON", e);
        } finally {
            session.close();
        }
        return null;
    }

    private void checkSession(EntityReference reference, Map<String, Object> params, String requestingAgent) {

        String sessionId = (String) params.get("sessionid");

		if (StringUtils.isBlank(sessionId)) {
            throw new EntityException("You must supply a sessionid.", reference.getReference());
        }

        org.sakaiproject.tool.api.Session session = sessionManager.getSession(sessionId);

        // The login user for a requesting agent is the requestingAgent appended with -hedex-user (preferred)
        // or matching the requestingAgent
        String userEid = "";
        if(session != null) {
            userEid = session.getUserEid();
        }
        if (session == null || (!userEid.equals(requestingAgent + "-hedex-user") && !userEid.equals(requestingAgent))) {
            throw new EntityException("You must be logged in as the correct hedex user.", reference.getReference());
        }
    }

    private String getCheckedRequestingAgent(Map<String, Object> params, EntityReference reference) {

        String requestingAgent = (String) params.get(REQUESTING_AGENT);

		if (StringUtils.isBlank(requestingAgent)) {
            throw new EntityException("You must supply a RequestingAgent.", reference.getReference());
        }
        return requestingAgent;
    }

    private Date getValidatedDate(String dateString) {

        Date date = null;
        if (!StringUtils.isBlank(dateString)) {
            try {
                date = startDateFormat.parse(dateString);
            } catch (ParseException pe) {
                log.error("Failed to parse supplied date. The date must be in ISO8601 format.", pe);
            }
        }
        return date;
    }

    private String[] getTerms(Map<String, Object> params) {

        final String termsString = (String) params.get(TERMS);
        return termsString != null ? termsString.split(",") : new String[] {};
    }

    private String getPersonSisId(String personLmsId, HashMap<String, String> userLookup) {
        if(userLookup == null || personLmsId == null) {
            return(null);
        }
        String personSisId = userLookup.get(personLmsId);
        if(personSisId != null) {
            return(personSisId);
        }
        if(userLookup.containsKey(personLmsId)) {
            // User EID is unknown - don't keep trying to look it up
            return(null);
        }
        User user = null;
        try {
            user = UserDirectoryService.getUser(personLmsId);
        } catch (UserNotDefinedException e) {
            // This shouldn't actually happen
        }
        if(user == null) {
            return(null);
        }
        personSisId = user.getEid();
        userLookup.put(personLmsId, personSisId);
        return(personSisId);
    }

}
