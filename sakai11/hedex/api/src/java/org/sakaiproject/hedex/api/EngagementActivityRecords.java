package org.sakaiproject.hedex.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngagementActivityRecords {

    private String tenantId;
    private String batchId;
    private String batchGroupId;
    private String batchTransactionStatus;
    private String batchTransactionStatusMessage;
    private String batchDataSourceAgents;
    private List<EngagementActivityRecord> engagementActivity;
}
