package edu.nyu.classes.groupersync.api;

import org.sakaiproject.time.cover.TimeService;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class GroupInfo {

    private final GroupStatus status;
    private final String label;
    private final String grouperId;
    private final String sakaiId;
    private final boolean readyForUse;
    private final long lastSyncTime;

    public GroupInfo() {
        // Null object
        this(GroupStatus.AVAILABLE_FOR_SYNC, "", null, null, false, 0);
    }

    public GroupInfo(String label, String grouperId, String sakaiId, boolean readyForUse, long lastSyncTime) {
        this(GroupStatus.MARKED_FOR_SYNC, label, grouperId, sakaiId, readyForUse, lastSyncTime);
    }

    private GroupInfo(GroupStatus status, String label, String grouperId, String sakaiId, boolean readyForUse, long lastSyncTime) {
        this.status = status;
        this.label = label;
        this.grouperId = grouperId;
        this.sakaiId = sakaiId;
        this.readyForUse = readyForUse;
        this.lastSyncTime = lastSyncTime;
    }

    public static GroupInfo unknown() {
        return new GroupInfo(GroupStatus.UNKNOWN, "", null, null, false, 0);
    }

    public String getGrouperId() {
        return grouperId;
    }

    public String getSakaiId() {
        return sakaiId;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public String getLabel() {
        return label;
    }

    public boolean isReadyForUse() {
        return readyForUse;
    }

    public String getLastSyncLabel() {
        if ((System.currentTimeMillis() - this.lastSyncTime) < (24 * 60 * 60 * 1000)) {
            return formatDuration(System.currentTimeMillis() - this.lastSyncTime);
        } else {
            return formatDate(this.lastSyncTime);
        }
    }

    private static String formatDate(long dateMs) {
        SimpleDateFormat fmt = new SimpleDateFormat("MMM dd 'at' h:mm a");
        Date date = new Date(dateMs);

        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTime(date);

        if (now.get(Calendar.YEAR) != then.get(Calendar.YEAR)) {
            // Include the year if it's not the current year
            fmt = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a");
        }

        // Adjust for the user's timezone
        fmt.setTimeZone(TimeService.getLocalTimeZone());

        return fmt.format(date);
    }

    private static String formatDuration(long msDuration) {
        String[] units = new String[] {"day", "hour", "minute", "second"};
        long[] secondsPerUnit = new long[] { (24 * 60 * 60), (60 * 60), 60, 1 };

        long seconds = msDuration / 1000;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < units.length; i++) {
            long unitCount = seconds / secondsPerUnit[i];
            seconds -= unitCount * secondsPerUnit[i];

            if (unitCount > 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }

                sb.append(unitCount);
                sb.append(" ");
                sb.append(units[i]);

                // plural
                if (unitCount > 1) {
                    sb.append("s");
                }
            }
        }

        return sb.toString() + " ago";
    }

    public enum GroupStatus {
        AVAILABLE_FOR_SYNC("Inactive"),
        MARKED_FOR_SYNC("Active"),
        UNKNOWN("Unknown");

        private final String label;

        GroupStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
