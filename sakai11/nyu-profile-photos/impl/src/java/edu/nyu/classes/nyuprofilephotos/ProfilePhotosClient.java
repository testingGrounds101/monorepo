package edu.nyu.classes.nyuprofilephotos;

import java.io.*;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.cover.SqlService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


class ProfilePhotosClient {
    private static final Log LOG = LogFactory.getLog(ProfilePhotosClient.class);

    private static final int MAX_FAILURES = 10;
    private static final long RETRY_INTERVAL_MS = 10000;

    private static final int PAGESIZE = 10;
    private static final int NETID_BATCHSIZE = 256;

    // When fetching incrementals, go back this many days and request from there.
    private static final int MARGIN_DAYS = 7;

    private final String authURL;
    private final String profileURL;
    private final String oauthClientAuth;

    private String accessToken;

    private final ThumbnailWriter thumbnailWriter;

    private CloseableHttpClient httpclient;


    public ProfilePhotosClient(ThumbnailWriter thumbnailWriter) {
        this.authURL = HotReloadConfigurationService.getString("profile-photos.auth-url", "");
        this.profileURL = HotReloadConfigurationService.getString("profile-photos.profile-url", "");
        this.oauthClientAuth = HotReloadConfigurationService.getString("profile-photos.oauth-client-auth", "");

        this.thumbnailWriter = thumbnailWriter;

        if (this.authURL.isEmpty() || this.profileURL.isEmpty()) {
            throw new RuntimeException("Properties 'profile-photos.auth-url' and 'profile-photos.profile-url' must be set");
        }

        int timeout = 60000;

        httpclient = HttpClientBuilder
            .create()
            .setDefaultRequestConfig(RequestConfig.custom()
                                     .setConnectTimeout(timeout)
                                     .setSocketTimeout(timeout)
                                     .setConnectionRequestTimeout(timeout)
                                     .build())
            .build();
    }


    public void authenticate() {
        String username = HotReloadConfigurationService.getString("profile-photos.service-username", "");
        String password = HotReloadConfigurationService.getString("profile-photos.service-password", "");

        if (username.isEmpty() || password.isEmpty()) {
            throw new RuntimeException("Properties 'profile-photos.service-username' and 'profile-photos.service-password' must be set");
        }

        try {
            HttpPost post = new HttpPost(this.authURL + "/token");

            ArrayList<NameValuePair> postParameters;
            postParameters = new ArrayList<NameValuePair>();

            postParameters.add(new BasicNameValuePair("grant_type", "password"));
            postParameters.add(new BasicNameValuePair("username", username));
            postParameters.add(new BasicNameValuePair("password", password));
            postParameters.add(new BasicNameValuePair("scope", "openid"));

            post.addHeader("Authorization", "Basic " + this.oauthClientAuth);

            post.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));

            JsonNode response = parseJSONResponse(httpclient.execute(post));

            this.accessToken = response.get("access_token").asText();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // Parse out a JSON object or die trying
    private JsonNode parseJSONResponse(HttpResponse response) throws IOException {
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Unexpected HTTP status code: " + response.getStatusLine().getStatusCode());
        }

        if (response.getFirstHeader("Content-Type").getValue().indexOf("/json") < 0) {
            throw new RuntimeException("Expected a JSON content type.  Got: " + response.getFirstHeader("Content-Type"));
        }

        return new ObjectMapper().readTree(EntityUtils.toString(response.getEntity(), "UTF-8"));
    }


    public Date fullHarvest() throws Exception {
        Date startTime = new Date();

        Connection connection = null;

        try {
            connection = SqlService.borrowConnection();

            List<String> batch = new ArrayList();

            try (PreparedStatement ps = connection.prepareStatement("select distinct netid from nyu_t_users order by netid");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    batch.clear();

                    do {
                        if (!thumbnailWriter.hasThumbnail(rs.getString("netid"))) {
                            batch.add(rs.getString("netid"));
                        }
                    } while (batch.size() < NETID_BATCHSIZE && rs.next());

                    if (!batch.isEmpty()) {
                        fetchPhotosForNetIds(batch);
                    }
                }
            }
        } finally {
            if (connection != null) {
                SqlService.returnConnection(connection);
            }
        }

        return startTime;
    }


    private void fetchPhotosForNetIds(List<String> netids) throws Exception {
        ArrayList<String> workSet = new ArrayList(netids);

        // The idea: we have a set of netids we haven't yet successfully got
        // photos for.  We send the workset on each attempt around the loop
        // until we get back a 404 (meaning "nothing in this set was found").
        //
        // Doing things this way allows us to send a relatively large set of
        // netids per request and helps to quickly skip over those without
        // photos.
        while (workSet.size() > 0) {
            boolean success = false;

            for (int failureCount = 0; failureCount < MAX_FAILURES; failureCount++) {
                if (failureCount > 0) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException e) {}
                }

                URI url = new URIBuilder(this.profileURL)
                    .addParameter("netid", workSet.stream().collect(Collectors.joining(",")))
                    .build();

                LOG.info("Fetching URL: " + url);

                HttpGet get = new HttpGet(url);
                get.addHeader("Authorization", "Bearer " + this.accessToken);

                HttpResponse response = httpclient.execute(get);

                if (response.getStatusLine().getStatusCode() == 200) {
                    JsonNode json = parseJSONResponse(response);

                    if (json.size() == 0) {
                        // This shouldn't happen -- an empty set seems to be
                        // signified by a 404.  But just so we don't spin
                        // forever if that changes...
                        return;
                    }

                    for (JsonNode element : json) {
                        String netid = element.get("Netid").asText();

                        if (netid != null) {
                            workSet.remove(netid);
                        }
                    }

                    handleProfiles(json);
                    success = true;
                    break;
                } else {
                    LOG.info("Fetching netid list returned unexpected status code: " + response.getStatusLine().getStatusCode());
                    EntityUtils.consumeQuietly(response.getEntity());

                    switch (response.getStatusLine().getStatusCode()) {
                    case 403:
                        // Try getting another token and retry
                        LOG.info("fetchPhotosForNetIds: caught 403 response: will retry authenticating.");
                        LOG.info("fetchPhotosForNetIds: 403 Response from API: " + response.getStatusLine());

                        authenticate();
                        break;
                    case 404:
                        // OK.  We're all done with this set
                        return;
                    default:
                        LOG.error("fetchPhotosForNetIds: Unexpected response from API: " + response.getStatusLine());
                        break;
                    }
                }
            }

            if (!success) {
                throw new RuntimeException("Failure while fetching profile photos for netids");
            }
        }
    }


    // "2017-07-28" has some stuff...
    public Date incrementalHarvest(Date lastRunDate) throws Exception {
        LocalDate date = lastRunDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(MARGIN_DAYS);
        LocalDate today = LocalDate.now();

        while (date.compareTo(today) <= 0) {
            for (int offset = 0; ; offset += PAGESIZE) {
                boolean success = false;

                for (int failureCount = 0; !success && failureCount < MAX_FAILURES; failureCount++) {
                    if (failureCount > 0) {
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException e) {}
                    }

                    URI url = new URIBuilder(this.profileURL)
                        .addParameter("photoeffectivedate", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .addParameter("offset", String.valueOf(offset))
                        .addParameter("limit", String.valueOf(PAGESIZE))
                        .build();

                    LOG.info("Fetching URL: " + url);

                    HttpGet get = new HttpGet(url);
                    get.addHeader("Authorization", "Bearer " + this.accessToken);

                    HttpResponse response = httpclient.execute(get);

                    if (response.getStatusLine().getStatusCode() == 200) {
                        handleProfiles(parseJSONResponse(response));
                        success = true;
                    } else {
                        EntityUtils.consumeQuietly(response.getEntity());

                        switch (response.getStatusLine().getStatusCode()) {
                        case 403:
                            // Try getting another token and retry
                            LOG.info("incrementalHarvest: caught 403 response: will retry authenticating.");
                            LOG.info("incrementalHarvest: 403 Response from API: " + response.getStatusLine());

                            authenticate();
                            break;
                        case 404:
                            // OK...
                            success = true;
                            break;
                        default:
                            LOG.error("incrementalHarvest: Unexpected response from API: " + response.getStatusLine());
                            break;
                        }
                    }
                }

                if (success) {
                    break;
                } else {
                    throw new RuntimeException("Failure while fetching incremental update for profile photos");
                }
            }

            date = date.plusDays(1);
        }

        return Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }


    private void handleProfiles(JsonNode json) {
        if (!json.isArray()) {
            throw new RuntimeException("Expected a JSONArray");
        }

        Base64.Decoder decoder = Base64.getDecoder();

        for (JsonNode element : json) {
            String netid = element.get("Netid") == null ? "" : element.get("Netid").asText();

            if (netid.isEmpty()) {
                LOG.info("Empty netid for profile photo element.  Skipping.");
                continue;
            }

            try {
                byte[] jpegBytes = decoder.decode(element.get("Photo").asText());
                thumbnailWriter.generateThumbnail(netid, jpegBytes);
            } catch (Exception e) {
                LOG.info("Failed while generating thumbnail for netid: " + netid, e);
            }
        }
    }


    public void logout() {
        if (this.accessToken != null) {
            try {
                HttpPost post = new HttpPost(this.authURL + "/revoke");

                ArrayList<NameValuePair> postParameters;
                postParameters = new ArrayList<NameValuePair>();

                postParameters.add(new BasicNameValuePair("token_type_hint", "access_token"));
                postParameters.add(new BasicNameValuePair("token", this.accessToken));

                post.addHeader("Authorization", "Basic " + this.oauthClientAuth);

                post.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));

                EntityUtils.consumeQuietly(this.httpclient.execute(post).getEntity());
            } catch (IOException e) {
                try {
                    this.httpclient.close();
                } catch (IOException e2) {}
                throw new RuntimeException(e);
            }
        }
    }
}
