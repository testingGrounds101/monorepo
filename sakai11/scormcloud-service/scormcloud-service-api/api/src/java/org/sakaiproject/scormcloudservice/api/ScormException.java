package org.sakaiproject.scormcloudservice.api;

public class ScormException extends Exception {
    public ScormException(String msg) {
        super(msg);
    }

    public ScormException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
