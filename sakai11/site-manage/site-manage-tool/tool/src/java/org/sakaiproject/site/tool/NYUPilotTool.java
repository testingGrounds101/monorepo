package org.sakaiproject.site.tool;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NYUPilotTool {
    String toolName;
    String toolDescription;
    String toolLearnMoreUrl;
    String school;
    String department;
    String subjectCode;
}
