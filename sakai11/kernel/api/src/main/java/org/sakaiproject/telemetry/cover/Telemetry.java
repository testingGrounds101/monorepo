package org.sakaiproject.telemetry.cover;

import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.Set;

import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.component.cover.ServerConfigurationService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.util.Collection;


/*
Oracle
------

create table nyu_t_telemetry (
  id varchar2(36) primary key,
  key varchar2(128),
  subkey varchar2(128),
  host varchar2(64),
  time number,
  value number
);

create index nyu_i_telemetry_key on nyu_t_telemetry(key, subkey, host, time);

*/


public class Telemetry
{
    private static Log LOG = LogFactory.getLog(Telemetry.class);
    private static Map<String, BaseMetric> metrics;
    private static Map<String, Long> retentionDays;
    private static ArrayBlockingQueue<DBUpdate> updateQueue = new ArrayBlockingQueue<>(524288);
    private static Thread telemetryWriterThread;


    //// Metric types
    abstract private static class BaseMetric {
        abstract public List<DBUpdate> produceUpdates(String name,
                                                      long time,
                                                      long value);

        protected long quantizeToDuration(long time, long duration) {
            return (time / duration) * duration;
        }
    }


    // A timer records how long some given activity took.
    private static class TimerMetric extends BaseMetric {
        @Override
        public List<DBUpdate> produceUpdates(String name, long time, long duration) {
            return Arrays.asList(new DBUpdate(DBUpdateType.INSERT,
                                              name,
                                              null,
                                              time,
                                              duration));
        }
    }


    // A histogram records how long some given activity took and increments the
    // value in a corresponding bucket.
    private static class HistogramMetric extends BaseMetric {
        private long intervalMS;
        private List<Long> bucketOffsets;
        private String greaterThanMaxLabel;

        public HistogramMetric(long intervalSeconds, String bucketDef) {
            this.intervalMS = intervalSeconds * 1000;

            String[] bits = bucketDef.split(", *");
            if (bits.length <= 1) {
                throw new IllegalArgumentException("Invalid buckets definition for histogram: " + bucketDef);
            }

            bucketOffsets = new ArrayList<>();
            bucketOffsets.add(0L);

            // The last bucket can be non-numeric to allow for labels like "2000+"
            for (int i = 0; i < bits.length - 1; i++) {
                long offset = Long.valueOf(bits[i]);

                // Skip zeroes because we've already added our own.
                if (offset > 0) {
                    bucketOffsets.add(offset);
                }
            }

            // The last bucket is always our catch-all
            this.greaterThanMaxLabel = bits[bits.length - 1];
        }

        public List<DBUpdate> produceUpdates(String name, long time, long value) {
            String bucket = null;

            for (int i = 1; i < this.bucketOffsets.size(); i++) {
                if (value < this.bucketOffsets.get(i)) {
                    // Found our bucket
                    bucket = String.format("%05d - %05d",
                                           this.bucketOffsets.get(i - 1),
                                           this.bucketOffsets.get(i));
                    break;
                }
            }

            if (bucket == null) {
                // If none of our offsets matched, we're greater than the max.
                // Land this reading in the catch-all bucket.
                bucket = this.greaterThanMaxLabel;
            }

            return Arrays.asList(new DBUpdate(DBUpdateType.INCREMENT,
                                              name,
                                              bucket,
                                              quantizeToDuration(time, this.intervalMS),
                                              1));
        }
    }


    // A counter records how many activities of a given type occurred within a
    // defined interval.
    private static class CounterMetric extends BaseMetric {
        private long intervalMS;

        public CounterMetric(long intervalSeconds) {
            this.intervalMS = intervalSeconds * 1000;
        }

        public List<DBUpdate> produceUpdates(String name, long time, long value) {
            return Arrays.asList(new DBUpdate(DBUpdateType.INCREMENT,
                                              name,
                                              null,
                                              quantizeToDuration(time, this.intervalMS),
                                              value));
        }
    }


    /// Database mutations
    enum DBUpdateType {
        INCREMENT,
        INSERT,
    }

    private static class DBUpdate {
        public DBUpdateType type;
        public String key;
        public String subkey;
        public long time;
        public long value;

        public DBUpdate(DBUpdateType type, String key, String subkey, long time, long value) {
            this.type = type;
            this.key = key;
            this.subkey = subkey;
            this.time = time;
            this.value = value;

            if (this.subkey == null) {
                this.subkey = this.key;
            }
        }

        public void setSubkey(String subkey) {
            this.subkey = subkey;
        }
    }


    /// Load all metric definitions from sakai.properties
    private static void loadMetricDefinitions() {
        metrics = new HashMap<>();
        retentionDays = new HashMap<>();

        String metricNames = ServerConfigurationService.getString("telemetry.metrics", "");

        if (metricNames == null || metricNames.isEmpty()) {
            // No metrics defined
            return;
        }

        for (String metric : metricNames.split(", *")) {
            String metricType = ServerConfigurationService.getString("telemetry." + metric, null);

            if (metricType == null) {
                LOG.warn(String.format("No metric type found for metric: %s.  Skipping this definition.",
                                       metric));
                continue;
            }

            if ("timer".equals(metricType)) {
                metrics.put(metric, new TimerMetric());
            } else if ("histogram".equals(metricType)) {
                long intervalSeconds = Long.valueOf(ServerConfigurationService.getString("telemetry." + metric + ".interval_seconds",
                                                                                         "INTERVAL_NOT_SET"));

                String bucketDef = ServerConfigurationService.getString("telemetry." + metric + ".buckets", "");

                try {
                    metrics.put(metric, new HistogramMetric(intervalSeconds, bucketDef));
                } catch (Exception e) {
                    LOG.warn(String.format("Defining metric %s failed with error: %s.  Skipping this definition.",
                                           metric, e));
                    e.printStackTrace();
                    continue;
                }
            } else if ("counter".equals(metricType)) {
                long intervalSeconds = Long.valueOf(ServerConfigurationService.getString("telemetry." + metric + ".interval_seconds",
                                                                                         "INTERVAL_NOT_SET"));

                try {
                    metrics.put(metric, new CounterMetric(intervalSeconds));
                } catch (Exception e) {
                    LOG.warn(String.format("Defining metric %s failed with error: %s.  Skipping this definition.",
                                           metric, e));
                    e.printStackTrace();
                    continue;
                }
            } else {
                LOG.warn(String.format("Invalid type for metric %s: %s.  Skipping this definition.",
                                       metric, metricType));
                continue;
            }

            try {
                String retention = ServerConfigurationService.getString("telemetry." + metric + ".retention_days", "");
                if (retention != null && !retention.isEmpty()) {
                    retentionDays.put(metric, Long.valueOf(retention));
                }
            } catch (Exception e) {
                LOG.warn(String.format("Retention days for metric %s could not be parsed: %s.  Metric discarded.",
                                       metric, e));
                metrics.remove(metric);
            }
        }
    }

    //// House keeping

    private static void recordUpdates(List<DBUpdate> updates) {
        for (DBUpdate update : updates) {
            if (!updateQueue.offer(update)) {
                LOG.error("Metric queue filled up.  Discarding metrics!");
                break;
            }
        }
    }

    // The fields which uniquely identify a given telemetry row
    private static class TelemetryKey {
        public String key;
        public String subkey;
        public long time;

        public TelemetryKey(String key, String subkey, long time) {
            this.key = key;
            this.subkey = subkey;
            this.time = time;
        }

        public boolean equals(Object obj) {
            return (obj instanceof TelemetryKey) && ((TelemetryKey) obj).stringKey().equals(this.stringKey());
        }

        public int hashCode() {
            return this.stringKey().hashCode();
        }

        public String stringKey() {
            return key + "___" + subkey + "___" + time;
        }
    }

    private static void flushInserts(Connection connection, List<DBUpdate> updates, String serverId) throws SQLException {
        try (PreparedStatement inserts = connection.prepareStatement("insert into nyu_t_telemetry (id, key, subkey, host, time, value) values (?, ?, ?, ?, ?, ?)")) {

            for (DBUpdate update : updates) {
                if (!DBUpdateType.INSERT.equals(update.type)) {
                    continue;
                }

                inserts.setString(1, UUID.randomUUID().toString());
                inserts.setString(2, update.key);
                inserts.setString(3, update.subkey);
                inserts.setString(4, serverId);
                inserts.setLong(5, update.time);
                inserts.setLong(6, update.value);

                inserts.addBatch();
            }

            inserts.executeBatch();
        }
    }

    private static void flushIncrements(Connection connection, List<DBUpdate> updates, String serverId) throws SQLException {
        // Since we're the only thing updating the DB for this host, we can
        // safely pull back values, update them in memory and write them out
        // again.
        //
        List<TelemetryKey> keysToIncrement = new ArrayList(new HashSet(updates
                                                                       .stream()
                                                                       .filter((update) -> { return DBUpdateType.INCREMENT.equals(update.type); })
                                                                       .map((update) -> { return new TelemetryKey(update.key, update.subkey, update.time); })
                                                                       .collect(Collectors.toList())));

        if (keysToIncrement.isEmpty()) {
            // Nothing further to do
            return;
        }

        Map<TelemetryKey, Long> existingValues = new HashMap<>();

        // Work in batches to pull back all values we can
        for (int start = 0; start < keysToIncrement.size(); start += 500) {
            int end = Math.min(keysToIncrement.size(), start + 500);

            List<TelemetryKey> sublist = keysToIncrement.subList(start, end);

            // Pull back the rows we already have
            StringBuilder whereClauses = new StringBuilder();

            for (TelemetryKey key : sublist) {
                if (whereClauses.length() > 0) {
                    whereClauses.append(" OR ");
                }

                whereClauses.append("(key = ? AND subkey = ? AND host = ? AND time = ?)");
            }

            String sql = "SELECT key, subkey, host, time, value from nyu_t_telemetry where " + whereClauses.toString();

            try (PreparedStatement select = connection.prepareStatement(sql)) {

                int field = 1;
                for (TelemetryKey key : sublist) {
                    select.setString(field, key.key);
                    select.setString(field + 1, key.subkey);
                    select.setString(field + 2, serverId);
                    select.setLong(field + 3, key.time);
                    field += 4;
                }

                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        existingValues.put(new TelemetryKey(rs.getString("key"), rs.getString("subkey"), rs.getLong("time")),
                                           rs.getLong("value"));
                    }
                }
            }
        }

        // Derive replacement values
        Map<TelemetryKey, Long> replacementValues = new HashMap<>(existingValues);

        for (DBUpdate update : updates) {
            if (!DBUpdateType.INCREMENT.equals(update.type)) {
                continue;
            }

            TelemetryKey updateKey = new TelemetryKey(update.key, update.subkey, update.time);

            if (!replacementValues.containsKey(updateKey)) {
                replacementValues.put(updateKey, 0L);
            }

            // Increment by the desired amount
            replacementValues.put(updateKey, replacementValues.get(updateKey) + update.value);
        }

        try (PreparedStatement insertRows = connection.prepareStatement("insert into nyu_t_telemetry (id, key, subkey, host, time, value) values (?, ?, ?, ?, ?, ?)");
             PreparedStatement updateRows = connection.prepareStatement("update nyu_t_telemetry set value = ? where key = ? AND subkey = ? AND host = ? AND time = ?")) {

            for (TelemetryKey updateKey : replacementValues.keySet()) {
                if (existingValues.containsKey(updateKey)) {
                    // Update the row we have
                    updateRows.setLong(1, replacementValues.get(updateKey));
                    updateRows.setString(2, updateKey.key);
                    updateRows.setString(3, updateKey.subkey);
                    updateRows.setString(4, serverId);
                    updateRows.setLong(5, updateKey.time);
                    updateRows.addBatch();
                } else {
                    // New row
                    insertRows.setString(1, UUID.randomUUID().toString());
                    insertRows.setString(2, updateKey.key);
                    insertRows.setString(3, updateKey.subkey);
                    insertRows.setString(4, serverId);
                    insertRows.setLong(5, updateKey.time);
                    insertRows.setLong(6, replacementValues.get(updateKey));
                    insertRows.addBatch();
                }
            }

            insertRows.executeBatch();
            updateRows.executeBatch();
        }
    }

    private static void expireReadings(Connection connection, String serverId) throws SQLException {
        long now = System.currentTimeMillis();

        try (PreparedStatement deleteRows = connection.prepareStatement("delete from nyu_t_telemetry where key = ? AND host = ? AND time < ?")) {
            for (String metric : retentionDays.keySet()) {
                LOG.info(String.format("Deleting values for %s older than %d days", metric, retentionDays.get(metric)));

                long cutoff = now - (retentionDays.get(metric) * 24 * 60 * 60 * 1000);

                deleteRows.setString(1, metric);
                deleteRows.setString(2, serverId);
                deleteRows.setLong(3, cutoff);
                deleteRows.addBatch();
            }

            deleteRows.executeBatch();
        }
    }

    private static void flushPendingUpdates(String serverId) throws SQLException {
        List<DBUpdate> updates = new ArrayList<>();
        updateQueue.drainTo(updates);

        LOG.info(String.format("Writing %d pending telemetry updates to the DB", updates.size()));

        if (updates.size() == 0) {
            // Nothing to do
            return;
        }

        Connection connection = null;
        boolean oldAutoCommit = true;

        try {
            connection = SqlService.borrowConnection();
            oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            flushInserts(connection, updates, serverId);
            flushIncrements(connection, updates, serverId);

            // Periodically expire old values.
            if (Math.random() < 0.001) {
                LOG.info("Expiring old readings");
                expireReadings(connection, serverId);
            }

            connection.commit();
        } catch (Exception e) {
            LOG.error("Failure flushing updates: " + e);
            e.printStackTrace();

            if (connection != null) {
                connection.rollback();
            }
        } finally {
            if (connection != null) {
                connection.setAutoCommit(oldAutoCommit);
                SqlService.returnConnection(connection);
            }
        }
    }

    private static void startWriterThread() {
        final String hostname = ServerConfigurationService.getServerId();
        final long sleepDelay = Long.valueOf(ServerConfigurationService.getString("telemetry.metrics.write-frequency-ms", "60000"));

        telemetryWriterThread = new Thread(() -> {
                while (true) {
                    try {
                        try {
                            Thread.sleep(sleepDelay);
                        } catch (InterruptedException e) {}
                        flushPendingUpdates(hostname);
                    } catch (Throwable t) {
                        LOG.error("Error from TelemetryWriterThread: " + t);
                    }
                }
        });

        telemetryWriterThread.setName("NYUTelemetryWriterThread");

        LOG.info("Telemetry writer thread starting up");

        telemetryWriterThread.start();
    }


    /// Public entry points for recording telemetry events
    public static void addToCount(String metricName, String subkey, long count) {
        BaseMetric metric = metrics.get(metricName);

        if (metric == null) {
            LOG.warn(String.format("Invalid addToCount metric requsted: %s", metricName));
            return;
        }


        List<DBUpdate> updates = metric.produceUpdates(metricName, System.currentTimeMillis(), count);

        if (subkey != null) {
            for (DBUpdate update : updates) {
                update.setSubkey(subkey);
            }
        }

        recordUpdates(updates);
    }


    public static class TelemetryTimer {
        private String metricName;
        private long startTime;

        public TelemetryTimer(String metricName, long startTime) {
            this.metricName = metricName;
            this.startTime = startTime;
        }

        public String getMetricName() { return metricName; }
        public long getStartTime() { return startTime; }
    }


    // Start time recording
    public static TelemetryTimer startTimer(String metricName) {
        return new TelemetryTimer(metricName, System.currentTimeMillis());
    }


    // Finish time recording and log to the DB
    public static void finishTimer(TelemetryTimer timer, String subkey) {
        if (timer == null) {
            // Handle nulls for the convenience of the caller.
            return;
        }

        BaseMetric metric = metrics.get(timer.getMetricName());

        if (metric == null) {
            LOG.warn(String.format("Invalid addToCount metric requsted: %s", timer.getMetricName()));
            return;
        }

        long finishTime = System.currentTimeMillis();
        long duration = finishTime - timer.getStartTime();

        if (duration < 0) {
            // Don't let's be silly!
            duration = 0;
        }

        List<DBUpdate> updates = metric.produceUpdates(timer.getMetricName(), timer.getStartTime(), duration);

        if (subkey != null) {
            for (DBUpdate update : updates) {
                update.setSubkey(subkey);
            }
        }

        recordUpdates(updates);
    }

    public static void finishTimer(TelemetryTimer timer) {
        finishTimer(timer, null);
    }


    /// Public entry points for accessing telemetry data
    public enum MetricType {
        TIMER,
        HISTOGRAM,
        COUNTER,
    }

    public static class TelemetryReading {
        private MetricType metricType;
        private String key;
        private String subKey;
        private long time;
        private long value;

        public TelemetryReading(MetricType metricType, String key, String subKey, long time, long value) {
            this.metricType = metricType;
            this.key = key;
            this.subKey = subKey;
            this.time = time;
            this.value = value;
        }

        public MetricType getMetricType() { return metricType; }
        public String getKey() { return key; }
        public String getSubKey() { return subKey; }
        public long getTime() { return time; }
        public long getValue() { return value; }

        public String toString() {
            return String.format("#<TelemetryReading type=%s key=%s subkey=%s time=%d value=%d>",
                                 metricType, key, subKey, time, value);
        }
    }

    public static Collection<String> listMetricNames() {
        return metrics.keySet();
    }

    // Fetch all readings since (whenever)
    //
    // For counters and histograms, we'll sum them across hosts and return single (aggregated) readings
    public static Collection<TelemetryReading> fetchReadings(String metricName, long since, int maxReadings) {
        Map<String, TelemetryReading> groupedReadings = new HashMap<>();
        Connection connection = null;

        BaseMetric metric = metrics.get(metricName);

        MetricType metricType = null;
        if (metric instanceof TimerMetric) {
            metricType = MetricType.TIMER;
        } else if (metric instanceof HistogramMetric) {
            metricType = MetricType.HISTOGRAM;
        } else if (metric instanceof CounterMetric) {
            metricType = MetricType.COUNTER;
        } else {
            throw new RuntimeException("Unrecognized metric type for: " + metricName);
        }

        try {
            connection = SqlService.borrowConnection();

            // Subkey strings tend to be identical across many readings.  Use
            // the same String instance for each one to avoid having each
            // reading consume a lot of redundant memory.
            //
            // (This is effectively String.intern() but managed ourselves.
            // Don't want to clog up the shared String pool for this...)
            //
            Map<String, String> subKeyPool = new HashMap<>();

            // Ordering by time descending here to return the latest readings in the case where maxReadings is hit.
            try (PreparedStatement ps = connection.prepareStatement("select subkey, host, time, value" +
                                                                    " from nyu_t_telemetry" +
                                                                    " where time >= ? AND key = ? order by time desc, subkey desc")) {
                ps.setLong(1, since);
                ps.setString(2, metricName);

                int rowCount = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    rs.setFetchSize(1024);
                    while (rs.next()) {
                        String subkey = rs.getString("subkey");

                        if (subKeyPool.containsKey(subkey)) {
                            subkey = subKeyPool.get(subkey);
                        } else {
                            subKeyPool.put(subkey, subkey);
                        }

                        TelemetryReading reading = new TelemetryReading(metricType,
                                                                        metricName,
                                                                        subkey,
                                                                        rs.getLong("time"),
                                                                        rs.getLong("value"));

                        if (metric instanceof TimerMetric) {
                            // No merge.  Just take the reading as is.
                            if (groupedReadings.size() < maxReadings) {
                                groupedReadings.put(String.valueOf(rowCount), reading);
                            }
                        } else {
                            // Merge if we've already got a value for a given key/subkey/time
                            String groupKey = reading.getKey() + "::" + reading.getSubKey() + "::" + reading.getTime();

                            if (groupedReadings.containsKey(groupKey)) {
                                TelemetryReading existingReading = groupedReadings.get(groupKey);
                                groupedReadings.put(groupKey, new TelemetryReading(existingReading.getMetricType(),
                                                                                   existingReading.getKey(),
                                                                                   existingReading.getSubKey(),
                                                                                   existingReading.getTime(),
                                                                                   reading.getValue() + existingReading.getValue()));
                            } else {
                                if (groupedReadings.size() < maxReadings) {
                                    groupedReadings.put(groupKey, reading);
                                }
                            }
                        }

                        rowCount += 1;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (connection != null) {
                SqlService.returnConnection(connection);
            }
        }

        return groupedReadings.values();
    }


    static {
        loadMetricDefinitions();
        startWriterThread();
    }
}
