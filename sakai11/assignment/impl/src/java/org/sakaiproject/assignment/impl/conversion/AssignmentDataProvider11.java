package org.sakaiproject.assignment.impl.conversion;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.type.StringType;
import org.sakaiproject.assignment.api.conversion.AssignmentDataProvider;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class AssignmentDataProvider11 implements AssignmentDataProvider{

    @Setter private SessionFactory sessionFactory;

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> fetchAssignmentsToConvertByTerm() {
        Map<String, List<String>> result = new HashMap<>();
        try {
            List<Object[]> list = sessionFactory.getCurrentSession()
                    // here we order the assignments based on the sites created date so newer sites will import first
                    .createSQLQuery("SELECT aa.ASSIGNMENT_ID, to_char(ssp.value) v " +
                                    "FROM ASSIGNMENT_ASSIGNMENT aa " +
                                    "LEFT JOIN SAKAI_SITE ss ON (aa.CONTEXT = ss.SITE_ID) " +
                                    "LEFT JOIN SAKAI_SITE_PROPERTY ssp ON (ssp.SITE_ID = ss.SITE_ID) AND (ssp.name = :term_eid) " +
                                    "ORDER BY ss.CREATEDON DESC")
                    .addScalar("ASSIGNMENT_ID")
                    .addScalar("v")
                    .setParameter("term_eid", "term_eid", StringType.INSTANCE)
                    .list();

            for (Object[] elts : list) {
                String assignmentId = (String)elts[0];
                String termEid = (String)elts[1];

                if (termEid == null) {
                    termEid = "(no term)";
                }

                if (!result.containsKey(termEid)) {
                    result.put(termEid, new ArrayList<String>());
                }

                result.get(termEid).add(assignmentId);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    @Transactional(readOnly = true)
    public List<String> fetchAssignmentsToConvert() {
        List<String> list;
        try {
            list = sessionFactory.getCurrentSession()
                    // here we order the assignments based on the sites created date so newer sites will import first
                    .createSQLQuery("SELECT aa.ASSIGNMENT_ID FROM ASSIGNMENT_ASSIGNMENT aa LEFT JOIN SAKAI_SITE ss ON (aa.CONTEXT = ss.SITE_ID) ORDER BY ss.CREATEDON DESC")
                    .addScalar("ASSIGNMENT_ID", StringType.INSTANCE)
                    .list();

            return list;
        } catch (Exception e) {
            log.warn("could not query table ASSIGNMENT_ASSIGNMENT for assignments to migrate, {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public String fetchAssignment(String assignmentId) {
        try {
            String xml = (String) sessionFactory.getCurrentSession()
                    .createSQLQuery("SELECT XML FROM ASSIGNMENT_ASSIGNMENT WHERE ASSIGNMENT_ID = :id")
                    .addScalar("XML", StringType.INSTANCE)
                    .setParameter("id", assignmentId, StringType.INSTANCE)
                    .uniqueResult();

            return xml;
        } catch (Exception e) {
            log.warn("could not query table ASSIGNMENT_ASSIGNMENT for assignment {}, {}", assignmentId, e.getMessage());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public String fetchAssignmentContent(String contentId) {
        try {
            String xml = (String) sessionFactory.getCurrentSession()
                    .createSQLQuery("SELECT XML FROM ASSIGNMENT_CONTENT WHERE CONTENT_ID = :id")
                    .addScalar("XML", StringType.INSTANCE)
                    .setParameter("id", contentId, StringType.INSTANCE)
                    .uniqueResult();

            return xml;
        } catch (Exception e) {
            log.warn("could not query table ASSIGNMENT_CONTENT for assignment content {}, {}", contentId, e.getMessage());
        }
        return null;
    }


    @Override
    @Transactional(readOnly = true)
    public List<String> fetchAssignmentSubmissions(String assignmentId) {
        try {
            List<String> list = sessionFactory.getCurrentSession()
                    .createSQLQuery("SELECT XML FROM ASSIGNMENT_SUBMISSION WHERE CONTEXT = :id")
                    .addScalar("XML", StringType.INSTANCE)
                    .setParameter("id", assignmentId, StringType.INSTANCE)
                    .list();

            return list;
        } catch (Exception e) {
            log.warn("could not query table ASSIGNMENT_SUBMISSION for submissions to migrate, {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
