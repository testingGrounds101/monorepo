package edu.nyu.classes.groupersync.tool;

import edu.nyu.classes.groupersync.api.AddressFormatter;
import org.sakaiproject.site.api.Group;
import edu.nyu.classes.groupersync.api.GroupInfo;
import edu.nyu.classes.groupersync.api.GrouperSyncException;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroup;

class GroupView implements Comparable<GroupView> {
    private static final Log log = LogFactory.getLog(GroupView.class);

    private final AuthzGroup group;
    private final String displayString;

    private GroupInfo groupInfo;

    public GroupView(Group group, GrouperSyncService grouper) {
        this(group, group.getTitle(), grouper);
    }

    public GroupView(AuthzGroup group, String displayString, GrouperSyncService grouper) {
        this.group = group;
        this.displayString = (displayString == null) ? "" : displayString;

        this.groupInfo = GroupInfo.unknown();

        // One query per group, but we're expecting the number of groups to be small.
        try {
            this.groupInfo = grouper.getGroupInfo(group.getId());

            if (groupInfo == null) {
                // Null object
                groupInfo = new GroupInfo();
            }
        } catch (GrouperSyncException e) {
            log.error("Failed to get group info for group: " + group, e);
        }
    }

    public String toString() {
        return displayString;
    }

    public String getRoster() {
        return group.getProviderGroupId();
    }

    public String getStatus() {
        return groupInfo.getStatus().getLabel();
    }

    public String getLastSyncLabel() {
        return groupInfo.getLastSyncLabel();
    }

    public String getAddress() {
        if (groupInfo.getGrouperId() != null) {
            return AddressFormatter.format(groupInfo.getGrouperId());
        } else {
            return "";
        }
    }

    public String getUrl() {
        if (groupInfo.getGrouperId() != null) {
            return AddressFormatter.formatUrl(groupInfo.getGrouperId());
        } else {
            return "";
        }
    }

    public String getLabel() {
        return groupInfo.getLabel();
    }

    public String getSakaiGroupId() {
        return group.getId();
    }

    public boolean isAvailableForSync() {
        return groupInfo.getStatus().equals(GroupInfo.GroupStatus.AVAILABLE_FOR_SYNC);
    }

    public boolean isMarkedForSync() {
        return groupInfo.getStatus().equals(GroupInfo.GroupStatus.MARKED_FOR_SYNC);
    }

    public boolean isReadyForUse() {
        return groupInfo.isReadyForUse();
    }

    @Override
    public int compareTo(GroupView other) {
        return toString().compareTo(other.toString());
    }
}
