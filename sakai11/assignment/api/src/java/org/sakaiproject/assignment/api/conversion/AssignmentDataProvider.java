package org.sakaiproject.assignment.api.conversion;

import java.util.List;
import java.util.Map;

public interface AssignmentDataProvider {
    List<String> fetchAssignmentsToConvert();

    String fetchAssignment(String assignmentId);

    String fetchAssignmentContent(String contentId);

    List<String> fetchAssignmentSubmissions(String assignmentId);

    Map<String, List<String>> fetchAssignmentsToConvertByTerm();

}
