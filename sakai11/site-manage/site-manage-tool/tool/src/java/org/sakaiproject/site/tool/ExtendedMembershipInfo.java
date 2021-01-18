package org.sakaiproject.site.tool;

import org.sakaiproject.site.api.Site;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ExtendedMembershipInfo {
    private Site site;

    public ExtendedMembershipInfo(Site s) {
        site = s;
    }

    public String getId() { return site.getId(); }
    public String getTitle() { return site.getTitle(); }
    public String getUrl() { return site.getUrl(); }

    public String getDescription() { return site.getDescription(); }

    public String getFormattedPublicationStatus() {
        return site.isPublished() ? "Published" : "Unpublished";
    }

    public String getFormattedInstructorList() {
        Set<String> userIds = site.getUsersHasRole("Instructor");
        List<User> users = UserDirectoryService.getUsers(userIds);

        return users.stream()
            .map(u -> u.getDisplayName())
            .collect(Collectors.joining(", "));
    }

    public String getFormattedDateCreated() {
        // Need this check because site.getCreatedDate() throws a NPE if the column is null.
        Date created = (site.getCreatedTime() != null) ? site.getCreatedDate() : null;

        if (created == null) {
            return "";
        }

        return new SimpleDateFormat("dd MMM, YYYY").format(created);
    }

    public String getFormattedTerm() {
        String result = (String)site.getProperties().get("term");

        if (result == null) {
            return "";
        }

        return result;
    }

    public boolean getPublished() {
        return site.isPublished();
    }
}

