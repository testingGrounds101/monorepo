package org.sakaiproject.content.googledrive.google;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import edu.nyu.classes.groupersync.api.AddressFormatter;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import edu.nyu.classes.groupersync.api.GroupInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.content.googledrive.GoogleClient;
import org.sakaiproject.content.googledrive.Utils;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.util.BaseResourcePropertiesEdit;


public class FileImport {

    private GoogleClient google;
    private Drive drive;
    private ContentHostingService chs;
    private GrouperSyncService grouper;

    public FileImport(GoogleClient google, Drive drive, ContentHostingService chs, GrouperSyncService grouper) {
        this.google = google;
        this.drive = drive;
        this.chs = chs;
        this.grouper = grouper;
    }

    // Give everyone in `googleGroupIds` `role` permission to every file in `fileIds`.
    //
    // Returns a map from FileID to list of permission IDs that were created.
    public void importFiles(List<String> fileIds,
                            Map<String, List<String>> fileIdToPermissionIdMap,
                            String collectionId,
                            int notificationSetting,
                            List<AuthzGroup> sakaiGroups,
                            String role) throws Exception {

        GoogleClient.LimitedBatchRequest batch = google.getBatch(drive);

        for (String fileId : fileIds) {
            batch.queue(drive.files().get(fileId).setFields("id, name, mimeType, description, webViewLink, iconLink, thumbnailLink"),
                        new GoogleFileImporter(fileIdToPermissionIdMap.get(fileId),
                                               fileId, collectionId, notificationSetting,
                                               sakaiGroups, role));
        }


        batch.execute();
    }

    public int mapNotificationSetting(String notify) {
        int notificationSetting = NotificationService.NOTI_NONE;

        if ("r".equals(notify)) {
            notificationSetting = NotificationService.NOTI_REQUIRED;
        } else if ("o".equals(notify)) {
            notificationSetting = NotificationService.NOTI_OPTIONAL;
        }

        return notificationSetting;
    }


    public Groups resolveSiteGroups(String siteId, List<String> sakaiGroupIds) throws Exception {
        Site site = SiteService.getSite(siteId);

        Groups result = new Groups();

        result.sakaiGroups = new ArrayList<>();
        result.googleGroupIds = new ArrayList<>();

        for (String groupId : sakaiGroupIds) {
            GroupInfo googleGroupInfo = grouper.getGroupInfo(groupId);
            if (googleGroupInfo != null && googleGroupInfo.isReadyForUse()) {
                if (groupId.equals(site.getId())) {
                    // Whole site group
                    result.sakaiGroups.add(site);
                } else {
                    result.sakaiGroups.add(site.getGroup(groupId));
                }

                result.googleGroupIds.add(AddressFormatter.format(googleGroupInfo.getGrouperId()));
            }
        }

        return result;
    }

    public void applyGroupAccess(ContentResourceEdit resourceEdit, List<AuthzGroup> sakaiGroups) throws Exception {
        if (sakaiGroups.isEmpty()) {
            resourceEdit.setHidden();
        } else {
            // sakaiGroups will contain a mixture of Site and Group
            // objects.  We only need to explicitly handle the Group
            // ones.
            List<Group> groups = sakaiGroups
                .stream()
                .filter(obj -> obj instanceof Group)
                .map(obj -> (Group)obj)
                .collect(Collectors.toList());

            if (groups.isEmpty()) {
                if (AccessMode.GROUPED.equals(resourceEdit.getAccess())) {
                    // Clear other groups if there were some previously.
                    resourceEdit.clearGroupAccess();
                }
            } else {
                resourceEdit.setGroupAccess(groups);
            }

            resourceEdit.setReleaseTime(new Date());
        }
    }

    public static class Groups {
        public List<AuthzGroup> sakaiGroups;
        public List<String> googleGroupIds;
    }

    private class GoogleFileImporter extends JsonBatchCallback<File> {
        private List<String> permissionIds;
        private String fileId;
        private String collectionId;
        private int notificationSetting;
        private List<AuthzGroup> sakaiGroups;
        private String role;

        public GoogleFileImporter(List<String> permissionIds,
                                  String fileId,
                                  String collectionId,
                                  int notificationSetting,
                                  List<AuthzGroup> sakaiGroups,
                                  String role) {
            this.permissionIds = permissionIds;
            this.fileId = fileId;
            this.collectionId = collectionId;
            this.notificationSetting = notificationSetting;
            this.sakaiGroups = sakaiGroups;
            this.role = role;
        }

        public void onSuccess(File googleFile, HttpHeaders responseHeaders) {
            ResourceProperties properties = new BaseResourcePropertiesEdit();
            properties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, googleFile.getName());
            properties.addProperty("google-id", fileId);
            properties.addProperty("google-view-link", googleFile.getWebViewLink());
            properties.addProperty("google-icon-link", googleFile.getIconLink());
            properties.addProperty("google-mime-type", googleFile.getMimeType());
            properties.addProperty("google-group-role", role);

            Utils.storeStringArray(properties, "google-permission-id", permissionIds);

            try {
                ContentResource resource = chs.addResource(UUID.randomUUID().toString(),
                                                           collectionId,
                                                           10,
                                                           "x-nyu-google/item",
                                                           googleFile.getWebViewLink().getBytes(),
                                                           properties,
                                                           Collections.<String>emptyList(),
                                                           notificationSetting);

                ContentResourceEdit resourceEdit = chs.editResource(resource.getId());
                resourceEdit.setResourceType(ResourceType.TYPE_GOOGLE_DRIVE_ITEM);

                applyGroupAccess(resourceEdit, sakaiGroups);

                chs.commitResource(resourceEdit, NotificationService.NOTI_NONE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
            if (e.getCode() == 403) {
                google.rateLimitHit();
            }

            throw new GoogleUpdateFailureException("Failed during Google lookup for file: " + this.fileId,
                                                   e);
        }
    }
}
