package org.sakaiproject.hedex.api;

import lombok.Data;

@Data
public class EngagementActivityRecord {

    private String personSisId;
    private String personLmsId;
    private String sisSectionId;
    private String lmsSectionId;
    private String termCode;
    private String sectionRefNum;
    private String subjectCode;
    private String sectionCourseNumber;
    private String sectionNumber;
    private String engagementStatus;
    private long lmsLastAccessDate = 0L;
    private long lmsTotalTime = 0L;
    private long lmsTotalLogin = 0;
}
