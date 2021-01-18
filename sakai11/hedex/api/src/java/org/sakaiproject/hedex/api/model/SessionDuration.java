package org.sakaiproject.hedex.api.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HDX_SESSION_DURATION")
@Getter
@Setter
public class SessionDuration {

    @Id
    @Column(name = "ID", nullable = false)
    @GeneratedValue
    private Long id;

    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Column(name = "SESSION_ID", length = 36, unique = true, nullable = false)
    private String sessionId;

    @Column(name = "START_TIME", columnDefinition="DATETIME", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;

    @Column(name = "DURATION")
    private Long duration;

    @Column(name = "AGENT", nullable = false)
    private String agent;
}
