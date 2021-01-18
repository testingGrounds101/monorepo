package org.sakaiproject.hedex.api.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HDX_ASSIGNMENT_SUBMISSIONS"
        , uniqueConstraints=@UniqueConstraint(columnNames={"USER_ID", "ASSIGNMENT_ID"}))
@Getter
@Setter
public class AssignmentSubmissions {

    @Id
    @Column(name = "ID", nullable = false)
    @GeneratedValue
    private Long id;

    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Column(name = "ASSIGNMENT_ID", length = 36, nullable = false)
    private String assignmentId;

    @Column(name = "SUBMISSION_ID", length = 36, unique = true, nullable = false)
    private String submissionId;

    @Column(name = "SITE_ID", length = 36, nullable = false)
    private String siteId;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "DUE_DATE", columnDefinition="DATETIME", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dueDate;

    @Column(name = "NUM_SUBMISSIONS", nullable = false)
    private Integer numSubmissions;

    @Column(name = "NUM_GRADINGS")
    private Integer numGradings;

    @Column(name = "FIRST_SCORE")
    private String firstScore;

    @Column(name = "LAST_SCORE")
    private String lastScore;

    @Column(name = "AVG_SCORE")
    private Float averageScore;

    @Column(name = "LOWEST_SCORE")
    private Integer lowestScore;

    @Column(name = "HIGHEST_SCORE")
    private Integer highestScore;

    @Column(name = "AGENT", nullable = false)
    private String agent;
}
