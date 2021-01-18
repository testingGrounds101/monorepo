package org.sakaiproject.hedex.api;

import lombok.Data;

@Data
public class AssignmentRecord {

    private String personLmsId;
    private String personSisId;
    private String assignmentLmsId;
    private String assignType;
    private String assignTitle;
    private String assignDueDate;
    private String assignGrade;
    private String assignGradeScheme;
    private String assignScore;
    private String assignScoreScheme;
    private String assignHiScore;
    private String assignLoScore;
    private String assignFirstAttmpt;
    private String assignLastAttmpt;
    private String assignAvgAttmpt;
    private String assignNumAttempt;
}
