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

package org.sakaiproject.content.googledrive;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


// FIXME: split this class up.  Maybe want a .google package?
public class GoogleClient {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleClient.class);

    private int requestsPerBatch = 100;

    // FIXME: What should this number be?
    private RateLimiter rateLimiter = new RateLimiter(100000, 100000);

    private HttpTransport httpTransport = null;
    private JacksonFactory jsonFactory = null;
    private GoogleClientSecrets clientSecrets = null;

    public GoogleClient() {
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
            clientSecrets = GoogleClientSecrets.load(jsonFactory,
                    new InputStreamReader(new FileInputStream(ServerConfigurationService.getSakaiHomePath() + "/client_secrets.json")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getGoogleDomain() {
        return HotReloadConfigurationService.getString("resources-google-domain", "nyu.edu");
    }

    public static String getApplicationName() {
        return HotReloadConfigurationService.getString("resources-application-name", "NYU Classes");
    }

    public void migrateOAuthTokens() throws IOException {
        File oldDataStoreLocation = new File(ServerConfigurationService.getSakaiHomePath() + "/google-data-store");
        DataStoreFactory store = new DBDataStoreFactory();

        if (oldDataStoreLocation.exists()) {
            // Migrate to our new store
            LOG.info("Migrating OAuth tokens from old data store...");
            ((DBDataStore)store.getDataStore("StoredCredential")).populateFrom(new FileDataStoreFactory(oldDataStoreLocation).getDataStore("StoredCredential"));
            oldDataStoreLocation.renameTo(new File(ServerConfigurationService.getSakaiHomePath() + "/google-data-store.migrated"));
        }
    }

    public GoogleAuthorizationCodeFlow getAuthFlow() throws Exception {
        DataStoreFactory store = new DBDataStoreFactory();

        // set up authorization code flow
        return new GoogleAuthorizationCodeFlow.Builder(
                                                       httpTransport,
                                                       jsonFactory,
                                                       clientSecrets,
                                                       Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(store)
                .setApprovalPrompt("force")
                .setAccessType("offline")
                .build();
    }


    public void rateLimitHit() {
        rateLimiter.rateLimitHit();
    }

    public Credential getCredential() {
        return getCredential(GoogleClient.getCurrentGoogleUser());
    }

    public Credential getCredential(String user) {
        try {
            GoogleAuthorizationCodeFlow flow = getAuthFlow();

            Credential storedCredential = flow.loadCredential(user);

            if (storedCredential == null) {
                return null;
            }

            // Take our credential and wrap it in a GoogleCredential.  As far as
            // I can tell, all this gives us is the ability to update our stored
            // credentials as they get refreshed (using the
            // DataStoreCredentialRefreshListener).
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(flow.getTransport())
                    .setJsonFactory(flow.getJsonFactory())
                    .setClientSecrets(clientSecrets)
                    .addRefreshListener(new CredentialRefreshListener() {
                        public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse) {
                            LOG.error("OAuth token refresh error: {}", tokenErrorResponse);
                        }

                        public void onTokenResponse(Credential credential, TokenResponse tokenResponse) {
                            LOG.info("OAuth token was refreshed");
                        }
                    })
                    .addRefreshListener(new DataStoreCredentialRefreshListener(user, flow.getCredentialDataStore()))
                    .build();

            credential.setAccessToken(storedCredential.getAccessToken());
            credential.setRefreshToken(storedCredential.getRefreshToken());

            return credential;
        } catch (Exception e) {
            LOG.error("Exception during OAuth response handling: {}", e);
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteCredential() throws Exception {
        return deleteCredential(GoogleClient.getCurrentGoogleUser());
    }

    public boolean deleteCredential(String user) throws Exception {
        GoogleAuthorizationCodeFlow flow = getAuthFlow();

        DataStore<StoredCredential> credentialStore = flow.getCredentialDataStore();

        return (credentialStore.delete(user) != null);
    }

    public Drive getDrive(String googleUser) throws Exception {
        return new Drive.Builder(httpTransport, jsonFactory, getCredential(googleUser))
                .setApplicationName(getApplicationName())
                .build();
    }


    public LimitedBatchRequest getBatch(AbstractGoogleClient client) throws Exception {
        return new LimitedBatchRequest(client);
    }

    public class LimitedBatchRequest {

        // According to the docs, Google sets their maximum to 1000, but we
        // can't really use that without hitting the rate limit.  See
        // RateLimiter above.
        private LinkedList<AbstractGoogleJsonClientRequest> requests = new LinkedList<>();
        private LinkedList<JsonBatchCallback> callbacks = new LinkedList<>();
        private AbstractGoogleClient client;


        public LimitedBatchRequest(AbstractGoogleClient client) throws Exception {
            requests = new LinkedList<AbstractGoogleJsonClientRequest>();
            callbacks = new LinkedList<JsonBatchCallback>();

            this.client = client;
        }

        public void queue(AbstractGoogleJsonClientRequest request, JsonBatchCallback callback) {
            requests.add(request);
            callbacks.add(callback);
        }

        public void execute() throws Exception {
            if (requests.isEmpty()) {
                return;
            }

            while (executeNextBatch()) {
                // To glory!
            }
        }

        private boolean executeNextBatch() throws Exception {
            if (requests.isEmpty()) {
                return false;
            }

            BatchRequest batch = client.batch(new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                    request.setConnectTimeout(15 * 60000);
                    request.setReadTimeout(15 * 60000);
                }
            });

            for (int i = 0; !requests.isEmpty() && i < GoogleClient.this.requestsPerBatch; i++) {
                AbstractGoogleJsonClientRequest request = requests.pop();
                JsonBatchCallback callback = callbacks.pop();

                request.queue(batch, callback);
            }

            if (batch.size() > 0) {
                GoogleClient.this.rateLimiter.wantQueries(batch.size());

                long start = System.currentTimeMillis();
                LOG.info("Executing batch of size: {}", batch.size());
                batch.execute();
                LOG.info("Batch finished in {} ms", System.currentTimeMillis() - start);
            }

            return !requests.isEmpty();
        }
    }

    private class RateLimiter {
        private long queriesPerTimestep;
        private long timestepMs;

        public RateLimiter(long queriesPerTimestep, long timestepMs) {
            this.queriesPerTimestep = queriesPerTimestep;
            this.timestepMs = timestepMs;
        }

        // Google limits to 1500 queries per 100 seconds by default.  This
        // appears to include the subqueries of batch requests (i.e. a single
        // batch request doesn't just count as one query.)
        private List<Long> times = new ArrayList<>();
        private Map<Long, Long> queryCounts = new HashMap<>();

        // Express an interest in running `count` queries.  Block until that's
        // OK.
        public synchronized void wantQueries(long count) {
            if (count > queriesPerTimestep) {
                throw new RuntimeException("Can't execute that many concurrent queries: " + count);
            }

            while ((queriesInLastTimestep() + count) >= queriesPerTimestep) {
                LOG.warn("Waiting for rate limiter to allow another {} queries", count);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
            }

            // OK!
            recordQueries(count);
        }

        private void recordQueries(long count) {
            long now = System.currentTimeMillis();

            if (times.contains(now)) {
                queryCounts.put(now, queryCounts.get(now) + count);
            } else {
                times.add(now);
                queryCounts.put(now, count);
            }
        }


        private long queriesInLastTimestep() {
            long result = 0;
            long timestepStart = System.currentTimeMillis() - timestepMs;

            Iterator<Long> it = times.iterator();
            while (it.hasNext()) {
                long time = it.next();

                if (time < timestepStart) {
                    // Time expired.  No longer needed.
                    it.remove();
                    queryCounts.remove(time);
                } else {
                    result += queryCounts.get(time);
                }
            }

            return result;
        }

        public void rateLimitHit() {
            LOG.warn("Google rate limit hit!");
        }
    }

    public static String getCurrentGoogleUser() {
        return org.sakaiproject.user.cover.UserDirectoryService.getCurrentUser().getEid() + "@" + getGoogleDomain();
    }

    public static String getRedirectURL() {
        String redirectUrl = ServerConfigurationService.getString("resources-google-redirect-url", "");

        if (redirectUrl.isEmpty()) {
            return ServerConfigurationService.getServerUrl() + "/direct/google-drive/handle-google-auth";
        } else {
            return redirectUrl;
        }
    }
}
