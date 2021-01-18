package org.sakaiproject.content.impl;

import java.util.Calendar;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Telemetry {
    private final int NO_REPORT_YET = -1;

    private final Object mutex = new Object();
    private int lastWritePos = -1;
    private boolean overflowed = false;

    private int maxObservationCount;
    private int reportPeriod;
    private int timestepPeriod;
    private int timestepsPerReport;
    private long[] observations;

    private AtomicLong lastReportTime = new AtomicLong(NO_REPORT_YET);
    private AtomicLong operationsPending = new AtomicLong(0);
    private AtomicReference<TelemetryReport> lastReport = new AtomicReference<>();
    private Semaphore reportReadySemaphore = new Semaphore(0);

    private Logger log;

    public Telemetry(Logger log, int timestepPeriod, int reportPeriod, int maxObservationCount) {
        this.log = log;
        this.timestepPeriod = timestepPeriod;
        this.reportPeriod = reportPeriod;
        this.maxObservationCount = maxObservationCount;
        this.observations = new long[maxObservationCount];

        timestepsPerReport = (reportPeriod / timestepPeriod);

        Thread aggregation = new Thread(() -> { runAggregationLoop(); });
        aggregation.setDaemon(true);
        aggregation.start();

        Thread report = new Thread(() -> { runReportLoop(); });
        report.setDaemon(true);
        report.start();
    }

    public void operationPending() {
        operationsPending.incrementAndGet();
    }

    public void operationComplete() {
        operationsPending.decrementAndGet();
    }

    // Log a single observation and get out of the way as quickly as we can.
    public void addObservation(int observation) {
        synchronized(mutex) {
            lastWritePos++;

            if (lastWritePos >= maxObservationCount) {
                overflowed = true;
                lastWritePos = 0;
            }

            observations[lastWritePos] = observation;
        }
    }

    // Dart in and roll up the observations we've seen in the last timestep.
    // Periodically publishes a report to be logged.  On the critical path,
    // since it holds up observations being written while running.
    private void runAggregationLoop() {
        int currentTimestep = -1;
        TelemetryReport report = new TelemetryReport(timestepsPerReport);

        // Roughly line up our aggregation loop with the top of the minute.
        // Gives multiple telemetry outputs a chance of being comparable if they
        // cover the same range.
        try {
            Calendar now = Calendar.getInstance();
            Thread.sleep(60000 - (now.get(Calendar.SECOND) * 1000) + (now.get(Calendar.MILLISECOND)));

            // Discard our partial readings
            synchronized (mutex) {
                lastWritePos = -1;
                overflowed = false;
            }

        } catch (InterruptedException e) {}

        while (true) {
            currentTimestep++;

            try {
                Thread.sleep(timestepPeriod);
            } catch (InterruptedException e) {
                break;
            }

            synchronized (mutex) {
                if (overflowed) {
                    overflowed = false;

                    report.observationSums[currentTimestep] = -1;
                    report.operationsPendingCounts[currentTimestep] = -1;
                } else {
                    report.operationsPendingCounts[currentTimestep] = operationsPending.get();
                    report.operationsCompletedCount += lastWritePos + 1;

                    for (int i = 0; i <= lastWritePos; i++) {
                        report.observationSums[currentTimestep] += observations[i];
                    }

                    // The next timestep will write at 0
                    lastWritePos = -1;
                }

                // Publish our report for the last `reportPeriod` if we've
                // got a full set.
                if (currentTimestep + 1 == timestepsPerReport) {
                    lastReport.set(report);
                    lastReportTime.set(System.currentTimeMillis());
                    reportReadySemaphore.release();

                    currentTimestep = -1;
                    report = new TelemetryReport(timestepsPerReport);
                }
            }
        }
    }


    // Log published reports as they show up.
    private void runReportLoop() {
        long lastSeenReportTime = NO_REPORT_YET;

        while (true) {
            try {
                reportReadySemaphore.acquire();
            } catch (InterruptedException e) {
                break;
            }

            if (lastSeenReportTime != lastReportTime.get()) {
                TelemetryReport report = lastReport.get();
                long now = lastReportTime.get();

                long minimumTransfer = Long.MAX_VALUE;
                long maximumTransfer = Long.MIN_VALUE;
                long totalTransfer = 0;

                long minimumPending = Long.MAX_VALUE;
                long maximumPending = Long.MIN_VALUE;
                long totalPending = 0;

                int validTimestepCount = 0;

                for (int i = 0; i < timestepsPerReport; i++) {
                    if (report.observationSums[i] < 0) {
                        log.warn("WARNING: circular buffer overflowed.  Skipping this observation.");
                        continue;
                    }

                    validTimestepCount += 1;

                    if (report.observationSums[i] < minimumTransfer) {
                        minimumTransfer = report.observationSums[i];
                    }

                    if (report.observationSums[i] > maximumTransfer) {
                        maximumTransfer = report.observationSums[i];
                    }

                    totalTransfer += report.observationSums[i];

                    if (report.operationsPendingCounts[i] < minimumPending) {
                        minimumPending = report.operationsPendingCounts[i];
                    }

                    if (report.operationsPendingCounts[i] > maximumPending) {
                        maximumPending = report.operationsPendingCounts[i];
                    }

                    totalPending += report.operationsPendingCounts[i];
                }

                StringBuilder sb = new StringBuilder();

                if (minimumTransfer != Long.MAX_VALUE) {
                    sb.append(String.format("minimum xfr=%.2f KB/s", (minimumTransfer / (timestepPeriod / 1000.0) / 1024.0)));
                }
                if (maximumTransfer != Long.MIN_VALUE) {
                    if (sb.length() > 0) { sb.append("; "); }
                    sb.append(String.format("maximum xfr=%.2f KB/s", (maximumTransfer / (timestepPeriod / 1000.0) / 1024.0)));
                }
                if (validTimestepCount > 0) {
                    if (sb.length() > 0) { sb.append("; "); }
                    sb.append(String.format("average xfr=%.2f KB/s", (totalTransfer / validTimestepCount / (timestepPeriod / 1000.0) / 1024.0)));
                }

                if (minimumPending != Long.MAX_VALUE) {
                    if (sb.length() > 0) { sb.append("; "); }
                    sb.append(String.format("minimum pending=%d", minimumPending));
                }
                if (maximumPending != Long.MIN_VALUE) {
                    if (sb.length() > 0) { sb.append("; "); }
                    sb.append(String.format("maximum pending=%d", maximumPending));
                }
                if (validTimestepCount > 0) {
                    if (sb.length() > 0) { sb.append("; "); }
                    sb.append(String.format("average pending=%.2f", ((float)totalPending / validTimestepCount)));
                }

                if (sb.length() > 0) {
                    sb.append("; operations_completed=" + report.operationsCompletedCount);
                    log.info(now + " " + sb.toString());
                }

                lastSeenReportTime = now;
            }
        }
    }


    private static class TelemetryReport {
        public long[] observationSums;
        public long[] operationsPendingCounts;
        public long operationsCompletedCount;

        public TelemetryReport(int timestepsPerReport) {
            observationSums = new long[timestepsPerReport];
            operationsPendingCounts = new long[timestepsPerReport];
        }
    }
}
