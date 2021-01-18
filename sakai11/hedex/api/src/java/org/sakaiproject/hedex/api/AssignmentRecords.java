package org.sakaiproject.hedex.api;

import java.util.List;

import lombok.Data;

@Data
public class AssignmentRecords {

    private String tenantId;
    private String batchId;
    private String batchGroupId;
    private String batchTransactionStatus;
    private String batchTransactionStatusMessage;
    private String batchDataSourceAgents;
    private List<AssignmentRecord> assignments;
}
