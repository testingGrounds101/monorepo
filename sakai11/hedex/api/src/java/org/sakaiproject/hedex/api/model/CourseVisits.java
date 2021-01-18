package org.sakaiproject.hedex.api.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HDX_COURSE_VISITS"
        , uniqueConstraints=@UniqueConstraint(columnNames={"USER_ID", "SITE_ID"}))
@Getter
@Setter
public class CourseVisits {

    @Id
    @Column(name = "ID", nullable = false)
    @GeneratedValue
    private Long id;

    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Column(name = "SITE_ID", length = 36, nullable = false)
    private String siteId;

    @Column(name = "NUM_VISITS", nullable = false)
    private Long numVisits;

    @Column(name = "LATEST_VISIT", columnDefinition="DATETIME", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date latestVisit;

    @Column(name = "AGENT", nullable = false)
    private String agent;
}
