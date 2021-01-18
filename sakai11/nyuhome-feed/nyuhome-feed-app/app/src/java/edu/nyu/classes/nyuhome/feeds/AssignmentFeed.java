package edu.nyu.classes.nyuhome.feeds;

import lombok.*;
import lombok.experimental.Builder;


import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.List;

import edu.nyu.classes.nyuhome.api.QueryUser;
import edu.nyu.classes.nyuhome.api.DataFeedEntry;
import edu.nyu.classes.nyuhome.api.Resolver;

import org.sakaiproject.component.cover.ComponentManager;

import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.entity.api.ResourceProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;


@Builder
class AssignmentResponse implements DataFeedEntry {
    private Date openDate;
    private Date lastModified;

    @Getter private Date dueDate;
    @Getter private String id;
    @Getter private String creator__userid;
    @Getter private String context__siteid;
    @Getter private String title;
    @Getter private String instructions;
    @Getter private String reference;
    @Getter private String toolUrl;


    public Date getSortDate() {
        if (openDate.compareTo(lastModified) <= 0) {
            return openDate;
        } else {
            return lastModified;
        }
    }
}


public class AssignmentFeed extends SakaiToolFeed {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentFeed.class);

    public List<DataFeedEntry> getUserData(QueryUser user, Resolver resolver, int maxAgeDays, int maxResults) {
        List<DataFeedEntry> result = new ArrayList<DataFeedEntry>();
        MaxAgeAndCountFilter filter = new MaxAgeAndCountFilter(maxAgeDays, maxResults);

        AssignmentService assignmentService = (AssignmentService) ComponentManager.get("org.sakaiproject.assignment.api.AssignmentService");

        for (String siteId : user.listSites()) {
            Collection<Assignment> assignments = assignmentService.getAssignmentsForContext(siteId);

            for (Assignment assignment : assignments) {
                if (isAssignmentVisible(assignment)) {
                    AssignmentResponse response = prepareAssignment(assignment,
                                                                    assignmentService.assignmentReference(assignment.getId()));

                    if (filter.accept(response.getSortDate())) {
                        result.add(response);
                        resolver.addUser(response.getCreator__userid());
                        resolver.addSite(response.getContext__siteid());
                    }
                }
            }

            resolver.addSite(siteId);
        }

        return result;
    }


    private AssignmentResponse prepareAssignment(Assignment assignment, String assignmentReference) {
        return new AssignmentResponse.AssignmentResponseBuilder()
            .openDate(Date.from(assignment.getOpenDate()))
            .lastModified(Date.from(assignment.getDateModified()))
            .dueDate(Date.from(assignment.getDueDate()))
            .id(assignment.getId())
            .creator__userid(assignment.getAuthor())
            .context__siteid(assignment.getContext())
            .title(assignment.getTitle())
            .instructions(assignment.getInstructions())
            .reference(assignmentReference)
            .toolUrl(buildUrl(assignment.getContext(), "sakai.assignment.grades"))
            .build();
    }


    private boolean isAssignmentVisible(Assignment assignment) {
        Instant currentTime = Instant.now();

        return !assignment.getDeleted() &&
            assignment.getOpenDate() != null &&
            currentTime.isAfter(assignment.getOpenDate()) &&
            !assignment.getDraft();
    }

}
