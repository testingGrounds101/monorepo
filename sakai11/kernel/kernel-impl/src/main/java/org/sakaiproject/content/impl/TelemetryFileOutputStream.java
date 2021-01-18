package org.sakaiproject.content.impl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TelemetryFileOutputStream extends FileOutputStream {

    private static Logger log = LoggerFactory.getLogger(TelemetryFileOutputStream.class);

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

    public TelemetryFileOutputStream(String s) throws FileNotFoundException {
        super(s);
    }

    public TelemetryFileOutputStream(String s, boolean append) throws FileNotFoundException {
        super(s, append);
    }

    public TelemetryFileOutputStream(File f) throws FileNotFoundException {
        super(f);
    }

    public TelemetryFileOutputStream(File f, boolean append) throws FileNotFoundException {
        super(f, append);
    }

    public TelemetryFileOutputStream(FileDescriptor f) throws FileNotFoundException {
        super(f);
    }


    // Allow a caller to mark an operation as pending/complete.  Since the
    // constructor of this class actually performs I/O, this lets us capture
    // that operation too.
    public static void operationPending() { t.operationPending(); }
    public static void operationComplete() { t.operationComplete(); }


    // NOTE: We implicitly assume here that write(byte[]) doesn't call
    // write(byte[], int, int) to do its work.  That's currently true, but if
    // it changed we would end up double-counting!
    //
    @Override
    public void write(byte[] b) throws IOException {
        t.operationPending();
        try {
            super.write(b);
            t.addObservation(b.length);
        } finally {
            t.operationComplete();
        }
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        t.operationPending();
        try {
            super.write(b, off, len);
            t.addObservation(len);
        } finally {
            t.operationComplete();
        }
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
