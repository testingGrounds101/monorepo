package org.sakaiproject.content.googledrive.google;

import java.io.IOException;
import com.google.api.client.googleapis.json.GoogleJsonError;

public class GoogleUpdateFailureException extends RuntimeException {
    private static String formatError(GoogleJsonError cause) {
        String googleError = cause.toString();

        try {
            googleError = cause.toPrettyString();
        } catch (IOException e) {
            // Whatever man
        }

        return googleError;
    }

    public GoogleUpdateFailureException(String msg, GoogleJsonError cause) {
        super(msg, new RuntimeException(formatError(cause)));
    }
}
