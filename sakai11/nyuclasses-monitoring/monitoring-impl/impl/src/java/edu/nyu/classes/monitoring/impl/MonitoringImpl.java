package edu.nyu.classes.monitoring.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sakaiproject.component.cover.HotReloadConfigurationService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sakaiproject.event.cover.UsageSessionService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.api.Session;


public class MonitoringImpl {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoringImpl.class);

    private Thread thread = null;

    public void init()
    {
        LOG.info("Initializing MonitoringImpl");

        thread = new Thread(new CheckRunner(), "NYUClasses monitoring");
        thread.start();

        new Thread(new NYUClassesCacheEvictor(), "NYUClassesCacheEvictor").start();
    }

    public void destroy()
    {
        LOG.info("Destroying MonitoringImpl");
    }

    private static class CheckRunner implements Runnable {
        private static final int MAX_THREADS = 4096;
        private Thread[] activeThreads = new Thread[MAX_THREADS];

        // Pattern that matches a thread name with a start time appended
        private static final Pattern threadNamePattern = Pattern.compile("http-.*-[0-9]+-exec-[0-9]+::([0-9]+).*$");

        public void run() {
            LOG.info("CheckRunner starting up");

            String threadExcludePattern = null;
            Pattern threadExcludePatternRegex = null;

            while (true) {
                try {
                    // Pull in our config options in case they've been updated
                    int checkFrequencyMs = Integer.valueOf(HotReloadConfigurationService.getString("nyu.monitoring.check-frequency-ms", "45000"));
                    int longRunningMs = Integer.valueOf(HotReloadConfigurationService.getString("nyu.monitoring.long-running-request-ms", "60000"));
                    String newPattern = HotReloadConfigurationService.getString("nyu.monitoring.thread-exclude-pattern", "").trim();

                    // If the pattern was updated, compile and replace it
                    if (!newPattern.equals(threadExcludePattern)) {
                        threadExcludePattern = newPattern;
                        threadExcludePatternRegex = Pattern.compile(newPattern);
                    }

                    int threadCount = Thread.enumerate(activeThreads);
                    long now = System.currentTimeMillis();

                    // Check each thread of interest (Tomcat request threads)
                    eachThread:
                    for (int i = 0; i < threadCount; i++) {
                        Thread target = activeThreads[i];
                        String threadName = target.getName();

                        LOG.debug("Checking thread: " + threadName);

                        try {
                            Matcher m = threadNamePattern.matcher(threadName);
                            if (m.matches()) {
                                long startTime = Long.valueOf(m.group(1));
                                long runTime = (now - startTime);

                                // Complain if they've been running too long
                                if (runTime > longRunningMs) {
                                    StringBuilder sb = new StringBuilder();

                                    // Logging to System.err here ensure that
                                    // the thread name is logged before the
                                    // stack trace (otherwise, log buffering
                                    // could cause them to display out of order)
                                    sb.append("\n========================================================================\n");
                                    sb.append("Thread has been running for " + runTime + " ms: " + threadName + ".  Stack follows\n\n");
                                    for (StackTraceElement elt : target.getStackTrace()) {
                                        if (threadExcludePatternRegex.matcher(elt.toString()).matches()) {
                                            // Skip this thread
                                            continue eachThread;
                                        }

                                        sb.append(elt);
                                        sb.append("\n");
                                    }

                                    sb.append("========================================================================\n");

                                    System.err.println(sb.toString());

                                }
                            } else {
                                LOG.debug("no match");
                            }
                        } catch (RuntimeException e) {
                            LOG.warn("Problem checking status of thread: " + threadName, e);
                        }
                    }

                    try {
                        LOG.debug("CheckRunner sleeping " + checkFrequencyMs + " ms");
                        Thread.sleep(checkFrequencyMs);
                    } catch (InterruptedException e) {}
                } catch (RuntimeException e) {
                    LOG.warn("Runtime exception", e);
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ex) {}
                }
            }
        }
    }


    private static class NYUClassesCacheEvictor implements Runnable {
        final static int pollMs = 600000;

        public void run() {
            LOG.info("NYUClassesCacheEvictor starting up");

            while (true) {
                try {
                    if ("true".equals(HotReloadConfigurationService.getString("nyu.cache-evictor.disable", "false"))) {
                        LOG.info("NYUClassesCacheEvictor is disabled");
                    } else {
                        Session sakaiSession = SessionManager.getCurrentSession();
                        sakaiSession.setUserId("admin");
                        sakaiSession.setUserEid("admin");

                        UsageSessionService.startSession("admin", "127.0.0.1", "MonitoringImpl");

                        try {
                            LOG.info("NYUClassesCacheEvictor clearing old cache entries");
                            org.sakaiproject.memory.cover.MemoryServiceLocator.getInstance().evictExpiredMembers();
                        } finally {
                            UsageSessionService.logout();
                        }
                    }
                } catch (Throwable e) {
                    LOG.warn("Throwable caught: ", e);
                }

                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException ex) {}
            }
        }
    }

}
