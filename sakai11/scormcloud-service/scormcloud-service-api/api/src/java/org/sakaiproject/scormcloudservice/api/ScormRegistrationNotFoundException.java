package org.sakaiproject.scormcloudservice.api;

public class ScormRegistrationNotFoundException extends Exception {
    public ScormRegistrationNotFoundException(String registrationId) {
        super(registrationId);
    }

    public ScormRegistrationNotFoundException(String registrationId, Throwable cause) {
        super(registrationId, cause);
    }
}
