package org.sakaiproject.content.googledrive.google;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sakaiproject.content.googledrive.GoogleClient;


public class Permissions {

    private GoogleClient google;
    private Drive drive;
    private GoogleClient.LimitedBatchRequest batchInProgress;

    public Permissions(GoogleClient google, Drive drive) {
        this.google = google;
        this.drive = drive;
        this.batchInProgress = null;
    }

    private GoogleClient.LimitedBatchRequest getBatch() throws Exception {
        if (this.batchInProgress != null) {
            return this.batchInProgress;
        } else {
            return google.getBatch(drive);
        }
    }

    private void executeLater(GoogleClient.LimitedBatchRequest batch) {
        this.batchInProgress = batch;
    }

    // Remove a list of permissions for a given file.
    //
    // Lazy because this doesn't execute until a non-lazy action runs.
    public Permissions lazyRemovePermissions(String fileId, List<String> permissionIds) throws Exception {
        GoogleClient.LimitedBatchRequest batch = getBatch();

        // Delete any permission that is about to be replaced
        for (String permissionId : permissionIds) {
            batch.queue(drive.permissions().delete(fileId, permissionId),
                        new DeletePermissionHandler(google, fileId, permissionId));
        }


        executeLater(batch);

        return this;
    }

    // Give everyone in `googleGroupIds` `role` permission to every file in `fileIds`.
    //
    // Returns a map from FileID to list of permission IDs that were created.
    public Map<String, List<String>> applyPermissions(List<String> fileIds,
                                                      String role,
                                                      List<String> googleGroupIds)
        throws Exception {
        Map<String, List<String>> fileIdtoPermissionIdMap = new HashMap<>();

        GoogleClient.LimitedBatchRequest batch = getBatch();

        for (String fileId : fileIds) {
            for (String group : googleGroupIds) {
                Permission permission = new Permission().setRole(role).setType("group").setEmailAddress(group);
                batch.queue(drive.permissions().create(fileId, permission).setSendNotificationEmail(false),
                            new PermissionHandler(google, fileId, fileIdtoPermissionIdMap));
            }
        }

        batch.execute();

        return fileIdtoPermissionIdMap;
    }

    private class DeletePermissionHandler extends JsonBatchCallback<Void> {
        private GoogleClient google;
        private String fileId;
        private String permissionId;

        public DeletePermissionHandler(GoogleClient google, String fileId, String permissionId) {
            this.google = google;
            this.fileId = fileId;
            this.permissionId = permissionId;
        }

        public void onSuccess(Void ignored, HttpHeaders responseHeaders)  {
            // Great!
        }

        public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
            if (e.getCode() == 403) {
                google.rateLimitHit();
            }

            throw new GoogleUpdateFailureException("Failed to remove permission on file: " + this.fileId,
                                                   e);
        }

    }

    private class PermissionHandler extends JsonBatchCallback<Permission> {
        private GoogleClient google;
        private String fileId;
        private Map<String, List<String>> permissionMap;

        public PermissionHandler(GoogleClient google, String fileId, Map<String, List<String>> permissionMap) {
            this.google = google;
            this.fileId = fileId;
            this.permissionMap = permissionMap;
        }

        public void onSuccess(Permission permission, HttpHeaders responseHeaders) {
            if (!permissionMap.containsKey(this.fileId)) {
                permissionMap.put(this.fileId, new ArrayList<>(1));
            }

            permissionMap.get(this.fileId).add(permission.getId());
        }

        public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
            if (e.getCode() == 403) {
                google.rateLimitHit();
            }

            throw new GoogleUpdateFailureException("Failed to set permission on file: " + this.fileId,
                                                   e);
        }
    }


}
