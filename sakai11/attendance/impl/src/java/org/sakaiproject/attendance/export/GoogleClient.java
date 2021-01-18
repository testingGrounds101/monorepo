/**********************************************************************************
 *
 * Copyright (c) 2018 The Sakai Foundation
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

package org.sakaiproject.attendance.export;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import java.io.IOException;
import java.io.File;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// FIXME: split this class up.  Maybe want a .google package?
public class GoogleClient {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleClient.class);

    private static int REQUEST_TIMEOUT = 300000;

    private Properties oauthProperties = null;
    private HttpTransport httpTransport = null;
    private JacksonFactory jsonFactory = null;

    private static final Set<String> SCOPES = SheetsScopes.all();

    public GoogleClient(Properties oauthProperties) {
        try {
            this.oauthProperties = oauthProperties;
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Credential getCredential() throws Exception {
        String user = oauthProperties.getProperty("client_id");
        String secret = oauthProperties.getProperty("secret");

        Set<String> scopes = new HashSet<>(Arrays.asList(oauthProperties.getProperty("scopes").split(", *")));

        File dataStoreLocation = new File(oauthProperties.getProperty("credentials_path"));
        FileDataStoreFactory store = new FileDataStoreFactory(dataStoreLocation);

        // General idea: create the auth flow backed by a credential store;
        // check whether the store already has credentials for the user we
        // want.  If it doesn't, we go through the auth process.
        GoogleAuthorizationCodeFlow auth = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, user, secret, scopes)
            .setAccessType("offline")
            .setDataStoreFactory(store)
            .build();

        Credential storedCredential = null;

        storedCredential = auth.loadCredential(user);

        if (storedCredential == null) {
            throw new RuntimeException("No stored credential was found for user: " + user);
        }

        // Take our credential and wrap it in a GoogleCredential.  As far as
        // I can tell, all this gives us is the ability to update our stored
        // credentials as they get refreshed (using the
        // DataStoreCredentialRefreshListener).
        Credential credential = new GoogleCredential.Builder()
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setClientSecrets(user, secret)
            .addRefreshListener(new CredentialRefreshListener() {
                public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse) {
                    LOG.error("OAuth token refresh error: " + tokenErrorResponse);
                }

                public void onTokenResponse(Credential credential, TokenResponse tokenResponse) {
                    LOG.info("OAuth token was refreshed");
                }
            })
            .addRefreshListener(new DataStoreCredentialRefreshListener(user, store))
            .build();

        credential.setAccessToken(storedCredential.getAccessToken());
        credential.setRefreshToken(storedCredential.getRefreshToken());

        return credential;
    }

    private HttpRequestInitializer setTimeouts(HttpRequestInitializer next) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                next.initialize(httpRequest);
                httpRequest.setConnectTimeout(REQUEST_TIMEOUT);
                httpRequest.setReadTimeout(REQUEST_TIMEOUT);
            }
        };
    }


    public Sheets getSheets(String applicationName) throws Exception {
        return new Sheets.Builder(httpTransport, jsonFactory, setTimeouts(getCredential()))
            .setApplicationName(applicationName)
            .build();
    }

    public static Set<String> requiredScopes() {
        return SCOPES;
    }


}
