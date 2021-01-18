/**********************************************************************************
 *
 * Copyright (c) 2015 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.content.googledrive.handlers;

import org.sakaiproject.content.googledrive.GoogleClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.cover.SessionManager;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;


import java.util.Collections;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.File;

import java.net.URL;

public class OAuthHandler implements Handler {

    private String redirectTo = null;
    public static final int SEND_TO_GOOGLE = 0;
    public static final int HANDLE_OAUTH = 1;
    public static final int RESET = 2;

    private static final String APPLICATION = "Sakai Drive";

    private int mode = 0;

    private GoogleClient google = null;

    public OAuthHandler(int mode) {
        try {
            this.mode = mode;
            this.google = new GoogleClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            if (mode == HANDLE_OAUTH) {
                handleOAuth(request, response, context);
            } else if (mode == SEND_TO_GOOGLE) {
                sendToGoogle(request, response, context);
            } else if (mode == RESET) {
                String googleUser = (String) context.get("googleUser");

                if (googleUser != null) {
                    google.deleteCredential(googleUser);
                }

                sendToGoogle(request, response, context);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOAuth(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        GoogleAuthorizationCodeFlow flow = google.getAuthFlow();

        GoogleTokenResponse googleResponse = flow.newTokenRequest((String)request.getParameter("code"))
                .setRedirectUri(handleLoginURL((URL) context.get("baseURL")))
                .execute();

        flow.createAndStoreCredential(googleResponse, (String) context.get("googleUser"));

        redirectTo = (String)request.getParameter("state");
    }


    /** Authorizes the installed application to access user's protected data. */
    private void sendToGoogle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        URL baseURL = (URL) context.get("baseURL");

        GoogleAuthorizationCodeFlow flow = google.getAuthFlow();

        redirectTo = flow.newAuthorizationUrl()
                .setRedirectUri(handleLoginURL(baseURL))
                .setState(((URL) context.get("baseURL")).toString())
                .build();
    }


    private String handleLoginURL(URL baseURL) {
        return GoogleClient.getRedirectURL();
    }

    public boolean hasRedirect() {
        return (redirectTo != null);
    }

    public String getRedirect() {
        return redirectTo;
    }

    public Errors getErrors() {
        return null;
    }

    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }
}
