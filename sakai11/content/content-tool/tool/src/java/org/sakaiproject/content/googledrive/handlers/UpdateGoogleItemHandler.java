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

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import edu.nyu.classes.groupersync.api.AddressFormatter;
import edu.nyu.classes.groupersync.api.GroupInfo;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import java.io.IOException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.content.googledrive.GoogleClient;
import org.sakaiproject.content.googledrive.Utils;
import org.sakaiproject.content.googledrive.google.FileImport;
import org.sakaiproject.content.googledrive.google.Permissions;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.util.BaseResourcePropertiesEdit;

public class UpdateGoogleItemHandler implements Handler {

    private String redirectTo = null;
    private GrouperSyncService grouper = null;

    public UpdateGoogleItemHandler() {
        grouper = (GrouperSyncService) ComponentManager.get("edu.nyu.classes.groupersync.api.GrouperSyncService");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            GoogleClient google = new GoogleClient();
            Drive drive = google.getDrive((String) context.get("googleUser"));

            String resourceId = request.getParameter("resourceId");
            String[] sakaiGroupIds = request.getParameterValues("sakaiGroupId[]");
            String notify = request.getParameter("notify");
            String role = request.getParameter("role");

            ContentHostingService chs = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");
            String siteId = (String) context.get("siteId");

            FileImport fileImport = new FileImport(google, drive, chs, grouper);

            int notificationSetting = fileImport.mapNotificationSetting(notify);
            FileImport.Groups resolvedGroups = fileImport.resolveSiteGroups(siteId,
                                                                            (sakaiGroupIds == null) ? Collections.emptyList() :
                                                                            Arrays.asList(sakaiGroupIds));

            ContentResourceEdit resource = null;
            try {
                resource = chs.editResource(resourceId);
                ResourcePropertiesEdit properties = resource.getPropertiesEdit();

                // FIXME: constants for this stuff?
                String fileId = properties.getProperty("google-id");

                // Use these to update permissions on the google side
                List<String> previousPermissionIds = Utils.loadStringArray(properties, "google-permission-id");

                Map<String, List<String>> fileIdToPermissionsMap =
                    new Permissions(google, drive)
                    .lazyRemovePermissions(fileId, previousPermissionIds)
                    .applyPermissions(Arrays.asList(fileId),
                                      role,
                                      resolvedGroups.googleGroupIds);

                // replace role
                properties.removeProperty("google-group-role");
                properties.addProperty("google-group-role", role);

                List<String> permissionIds = fileIdToPermissionsMap.get(fileId);

                Utils.storeStringArray(properties, "google-permission-id", permissionIds);

                fileImport.applyGroupAccess(resource, resolvedGroups.sakaiGroups);

                // commit changes
                chs.commitResource(resource, notificationSetting);

                redirectTo = "";

            } catch (Exception e) {
                // force rollback and removal of lock
                if (resource != null) {
                    chs.cancelResource(resource);
                }

                throw e;
            }
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

    public boolean hasTemplate() {
        return false;
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
