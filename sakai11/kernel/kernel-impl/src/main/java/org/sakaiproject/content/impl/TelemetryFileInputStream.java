package org.sakaiproject.content.impl;
// 
// Incorporated from https://github.com/marktriggs/telemetry-file-input-stream
//
// =============================================================================
//
// A FileInputStream that logs throughput (KB/s) of reads.
//
// (Nothing really file-specific about most of it, actually--could easily be
// adapted for an InputStream instead)
//
// Makes reasonable attempts to be robust and low-overhead.  Here's the plan:
//
//   * Each TelemetryFileInputStream instance logs the size of each read to a
//     shared Telemetry instance.  Right now that means taking a mutex, but it
//     isn't held for long.  The observation is written to a circular buffer and
//     we cross our fingers that we don't run out of space.
//
//   * An aggregation thread wakes up periodically (1 second by default), grabs
//     the mutex and tallies up the observations seen in the last second.  The
//     aggregated "timestep" total is written to a second array, and the
//     circular buffer is cleared.
//
//   * When we have accumulated enough timestep values to cover a report period,
//     we publish a copy of the array (via AtomicReference).  The goal here is
//     to decouple the (critical path) aggregation process from IO.
//
//   * A third reporting thread wakes up periodically and checks to see whether a
//     new report has been published.  If so, it logs it.
//
// If the circular buffer fills up, it just wraps around but shouldn't cause
// problems.  A warning is logged if that happens and the affected stats are
// skipped.
//
// If logging stalls for some reason, it shouldn't matter: the aggregation
// thread will just publish reports that nobody ever reads.  Some people make
// good careers doing that.

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TelemetryFileInputStream extends FileInputStream {

    private static Logger log = LoggerFactory.getLogger(TelemetryFileInputStream.class);

    // The maximum number of observations you're expecting to receive during
    // `timestepPeriod`.  If you exceed this we'll wrap around and lose
    // data, but that case is detected and the report is discarded.
    //
    private static final int MAX_OBSERVATION_COUNT = 1000000;

    // How often we'll log some stats
    private static final int REPORT_PERIOD = 60000;

    // How often we'll roll up the observations seen so far.  Bigger number
    // means lower overhead but longer pauses.
    private static final int TIMESTEP_PERIOD = 1000;


    private static Telemetry t = new Telemetry(log, TIMESTEP_PERIOD, REPORT_PERIOD, MAX_OBSERVATION_COUNT);

    public TelemetryFileInputStream(String s) throws FileNotFoundException {
        super(s);
    }

    public TelemetryFileInputStream(File f) throws FileNotFoundException {
        super(f);
    }

    public TelemetryFileInputStream(FileDescriptor f) throws FileNotFoundException {
        super(f);
    }

    // Allow a caller to mark an operation as pending/complete.  Since the
    // constructor of this class actually performs I/O, this lets us capture
    // that operation too.
    public static void operationPending() { t.operationPending(); }
    public static void operationComplete() { t.operationComplete(); }

    // NOTE: We implicitly assume here that read(byte[]) doesn't call
    // read(byte[], int, int) to do its work.  That's currently true, but if
    // it changed we would end up double-counting!
    //
    @Override
    public int read(byte[] b) throws IOException {
        int result;

        t.operationPending();
        try {
            result = super.read(b);
            t.addObservation(result);
        } finally {
            t.operationComplete();
        }

        return result;
    }


    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result;

        t.operationPending();
        try {
            result = super.read(b, off, len);
            t.addObservation(result);
        } finally {
            t.operationComplete();
        }

        return result;
    }


    @Override
    public void close() throws IOException {
        t.operationPending();
        try {
            super.close();
        } finally {
            t.operationComplete();
        }
    }
}
