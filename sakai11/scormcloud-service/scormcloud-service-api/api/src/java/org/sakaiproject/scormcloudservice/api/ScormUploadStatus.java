package org.sakaiproject.scormcloudservice.api;

public class ScormUploadStatus {

    private int retry_count;
    private int max_retry_count;
    private String errorMessages;

    public ScormUploadStatus(int retry_count, int max_retry_count, String errorMessages) {
        this.retry_count = retry_count;
        this.max_retry_count = max_retry_count;
        this.errorMessages = errorMessages;
    }

    public boolean isNew() {
        return retry_count == 0;
    }

    public boolean isPermanentlyFailed() {
        return retry_count == max_retry_count;
    }

    public String getErrorMessages() {
        return errorMessages;
    }

}
