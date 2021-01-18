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
import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import edu.nyu.classes.groupersync.api.AddressFormatter;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import edu.nyu.classes.groupersync.api.GroupInfo;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;
import org.sakaiproject.content.googledrive.GoogleClient;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;


public class EditGoogleItemHandler implements Handler {

    private String redirectTo = null;
    private GrouperSyncService grouper = null;
    private ContentHostingService chs = null;

    public EditGoogleItemHandler() {
        grouper = (GrouperSyncService) ComponentManager.get("edu.nyu.classes.groupersync.api.GrouperSyncService");
        chs = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            GoogleClient google = new GoogleClient();
            Drive drive = google.getDrive((String) context.get("googleUser"));

            String resourceId = request.getParameter("resourceId");

            if (resourceId == null) {
                throw new RuntimeException("resourceId required");
            }

            ContentResource resource = chs.getResource(resourceId);
            ResourceProperties properties = resource.getProperties();
            List<String> accessGroups = new ArrayList<>(resource.getGroups());

            // Build group data
            List<SakaiGoogleGroup> wholeSite = new ArrayList<SakaiGoogleGroup>();
            List<SakaiGoogleGroup> sections = new ArrayList<SakaiGoogleGroup>();
            List<SakaiGoogleGroup> adhocGroups = new ArrayList<SakaiGoogleGroup>();

            Site site = SiteService.getSite((String)context.get("siteId"));

            // Mode will likely be INHERITED for files available to the whole site, but may also be SITE.
            boolean selected = !AccessMode.GROUPED.equals(resource.getAccess()) && !resource.isHidden();
            wholeSite.add(new SakaiGoogleGroup(site.getId(), site.getTitle(), grouper.getGroupInfo(site.getId()), selected));

            for (Group group : site.getGroups()) {
                GroupInfo groupInfo = grouper.getGroupInfo(group.getId());
                selected = accessGroups.stream().anyMatch(g -> group.getReference().equals(g));

                if (group.getProviderGroupId() == null) {
                    adhocGroups.add(new SakaiGoogleGroup(group.getId(), group.getTitle(), groupInfo, selected));
                } else {
                    sections.add(new SakaiGoogleGroup(group.getId(), group.getTitle(), groupInfo, selected));
                }
            }

            context.put("wholeSiteGroups", wholeSite);
            context.put("sections", sections);
            context.put("adhocGroups", adhocGroups);

            context.put("resourceId", resourceId);
            context.put("resourceIconLink", properties.get("google-icon-link"));
            context.put("resourceName", properties.get(ResourceProperties.PROP_DISPLAY_NAME));

            context.put("role", properties.get("google-group-role"));

            context.put("subpage", "edit_google_item");
            context.put("layout", false);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private class SakaiGoogleGroup extends GenericJson {
        @Key
        String sakaiGroupId;
        @Key
        String title;
        @Key
        boolean hasGoogleGroup;
        @Key
        boolean hasGoogleGroupPending;
        @Key
        String googleGroupId;
        @Key
        boolean selected;

        public SakaiGoogleGroup(String sakaiGroupId, String title, GroupInfo googleGroupInfo, boolean selected) {
            this.sakaiGroupId = sakaiGroupId;
            this.title = title;
            this.hasGoogleGroup = googleGroupInfo != null && googleGroupInfo.isReadyForUse();
            this.hasGoogleGroupPending = googleGroupInfo != null && !googleGroupInfo.isReadyForUse();
            if (this.hasGoogleGroup) {
                this.googleGroupId = AddressFormatter.format(googleGroupInfo.getGrouperId());
            }
            this.selected = selected;
        }
    }
}
