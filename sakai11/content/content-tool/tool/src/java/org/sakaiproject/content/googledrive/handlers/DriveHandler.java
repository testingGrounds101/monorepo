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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.ResourceLoader;

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
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.stream.Collectors;
import java.util.Collections;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;


import java.net.URL;

/**
 * A handler to access Google Drive file data
 */
public class DriveHandler implements Handler {

    private String redirectTo = null;

    public static final int RECENT = 0;
    public static final int MY_DRIVE = 1;
    public static final int STARRED = 2;

    private List<String> fileFieldsToRequest = Arrays.asList("id",
                                                     "name",
                                                     "mimeType",
                                                     "description",
                                                     "webViewLink",
                                                     "iconLink",
                                                     "thumbnailLink",
                                                     "modifiedTime",
                                                     "ownedByMe",
                                                     "permissions");

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDateTime( FormatStyle.MEDIUM ).withLocale( Locale.US ).withZone( ZoneId.systemDefault() );

    private static final ResourceLoader rb = new ResourceLoader("content");

    //The default model for the view is RECENT.
    private int mode = RECENT;

    public DriveHandler(int mode) {
        this.mode = mode;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            GoogleClient google = new GoogleClient();

            RequestParams p = new RequestParams(request);

            String user = (String) context.get("googleUser");

            FileList fileList = null;

            switch(mode){
                case RECENT:
                    fileList = getRecentFiles(google, user, p);
                    break;
                case MY_DRIVE:
                    fileList = getChildrenForContext(google, user, p);
                    break;
                case STARRED:
                    fileList = getChildrenForContext(google, user, p, true);
                    break;
                default:
                    throw new RuntimeException("DriveHandler mode not supported: " + mode);
            }

            List<GoogleItem> items = new ArrayList<>();

            for (File entry : fileList.getFiles()) {
                GoogleItem googleItem = new GoogleItem();
                googleItem.setId(entry.getId());
                googleItem.setName(entry.getName());
                googleItem.setIconLink(entry.getIconLink());
                googleItem.setThumbnailLink(entry.getThumbnailLink());
                googleItem.setViewLink(entry.getWebViewLink());
                googleItem.setMimeType(entry.getMimeType());
                googleItem.setModifiedTime(dateFormatter.format(Instant.ofEpochMilli(entry.getModifiedTime().getValue())));

                if(entry.getOwnedByMe()){
                    googleItem.setAccessType(rb.getString("googledrive.role.owner"));
                    googleItem.setReadOnly(false);
                } else {
                   googleItem.setAccessType(entry.getPermissions() == null ? rb.getString("googledrive.role.view") : rb.getString("googledrive.role.writer"));
                   googleItem.setReadOnly(entry.getPermissions() == null);
                }

                items.add(googleItem);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(response.getOutputStream(), new GoogleItemPage(items, fileList.getNextPageToken()));
        } catch (Exception e) {
            context.put("layout", false);

            try {
                response.setStatus(500);
                response.setHeader("Content-Type", "text/plain");
                response.getWriter().write(e.getMessage());
            } catch (IOException ioe) {}

            return;
        }
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

    public String getContentType() {
        return "text/json";
    }

    public boolean hasTemplate() {
        return false;
    }

    private FileList getRecentFiles(GoogleClient google, String user, RequestParams p) {
        try {
            Drive drive = google.getDrive(user);

            String query = p.getString("q", null);
            String pageToken = p.getString("pageToken", null);

            Drive.Files files = drive.files();
            Drive.Files.List list = files.list();

            list.setFields(String.format("nextPageToken, files(%s)", String.join(",", fileFieldsToRequest)));

            String queryString = "mimeType != 'application/vnd.google-apps.folder'";

            if (query == null) {
                // API restriction: We can only sort if we don't have a search query
                list.setOrderBy("viewedByMeTime desc");
            } else {
                queryString += " AND fullText contains '" + query.replace("'", "\\'") + "'";
            }

            list.setQ(queryString);
            list.setPageSize(50);

            if (pageToken != null) {
                list.setPageToken(pageToken);
            }

            return list.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FileList getChildrenForContext(GoogleClient google, String user, RequestParams p) {
        return getChildrenForContext(google, user, p, false);
    }

    private FileList getChildrenForContext(GoogleClient google, String user, RequestParams p, boolean starred) {
        try {
            Drive drive = google.getDrive(user);

            String folderId = p.getString("folderId", "root");
            String pageToken = p.getString("pageToken", null);
            String query = p.getString("q", null);

            Drive.Files files = drive.files();
            Drive.Files.List list = files.list();

            list.setFields(String.format("nextPageToken, files(%s)", String.join(",", fileFieldsToRequest)));

            String queryString = "'"+folderId+"' in parents";

            if (starred && folderId.equals("root")) {
                queryString = "starred";
            }

            if (query == null) {
                list.setOrderBy("folder,name");
            } else {
                queryString += " AND fullText contains '" + query.replace("'", "\\'") + "'";
            }

            if (pageToken != null) {
                list.setPageToken(pageToken);
            }

            list.setQ(queryString);
            list.setPageSize(50);

            return list.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class GoogleItemPage {
        public String nextPageToken;
        public List<GoogleItem> files;

        public GoogleItemPage(List<GoogleItem> files, String nextPageToken) {
            this.files = files;
            this.nextPageToken = nextPageToken;
        }
    }

    @NoArgsConstructor @AllArgsConstructor @Data
    private class GoogleItem {
        private String id;
        private String name;
        private String iconLink;
        private String thumbnailLink;
        private String viewLink;
        private String mimeType;
        private String accessType;
        private String modifiedTime;
        private boolean readOnly;

        public boolean isFolder() { return mimeType.equals("application/vnd.google-apps.folder"); }
    }
}
