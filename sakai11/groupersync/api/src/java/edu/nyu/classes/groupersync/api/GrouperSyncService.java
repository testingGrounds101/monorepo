package edu.nyu.classes.groupersync.api;
import java.util.Date;
import java.util.Set;

public interface GrouperSyncService {

    GroupInfo getGroupInfo(String sakaiGroupId) throws GrouperSyncException;

    void markGroupForSync(final String groupId, final String grouperGroupId, final String sakaiGroupId, final String description) throws GrouperSyncException;

    void updateDescription(final String groupId, final String description) throws GrouperSyncException;

    void deleteGroup(final String groupId) throws GrouperSyncException;

    boolean isGroupAvailable(final String groupId) throws GrouperSyncException;

    Set<UserWithRole> getMembers(final String groupId) throws GrouperSyncException;

    void recordChanges(final String groupId,
                       final Set<UserWithRole> addedUsers,
                       final Set<UserWithRole> droppedUsers,
                       final Set<UserWithRole> changedRoles) throws GrouperSyncException;

    Date getLastRunDate() throws GrouperSyncException;

    void setLastRunDate(final Date date) throws GrouperSyncException;

    public void deleteDetachedGroups() throws GrouperSyncException;

    void init();

    void destroy();
}
