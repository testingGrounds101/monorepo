package org.sakaiproject.assignment.impl.conversion;

import java.io.File;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.assignment.api.conversion.AssignmentConversionService;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.component.cover.ComponentManager;

import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.assignment.api.conversion.AssignmentDataProvider;
import org.sakaiproject.assignment.api.persistence.AssignmentRepository;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Slf4j
@DisallowConcurrentExecution
public class Assignment12ConversionJob implements Job {

    public static final String SIZE_PROPERTY = "length.attribute.property";
    public static final String NUMBER_PROPERTY = "number.attributes.property";

    @Setter
    private AssignmentConversionService assignmentConversionService;

    private static final int THREAD_COUNT = 24;
    private static final int ASSIGNMENTS_PER_THREAD = 128;

    private class ProcessedCount {
        public AtomicLong processedCount = new AtomicLong();
        public int totalCount;

        public ProcessedCount() {}

        public ProcessedCount(int total) {
            this.totalCount = total;
        }

        public String toString() {
            if (totalCount == 0) {
                return "Nothing to do";
            }

            int processed = this.processedCount.intValue();

            return String.format("Processed %d of %d (%.2f%% complete)",
                                 processed,
                                 totalCount,
                                 (((float)processed / totalCount) * 100));

        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("<===== Assignment Conversion Job start =====>");

        boolean dryRun = determineDryRun();

        log.info("Running in dry run mode? {}", dryRun);

        // never run as a recovery
        if (context.isRecovering()) {
            log.warn("<===== Assignment Conversion Job doesn't support recovery, job will terminate... =====>");
        } else {
            JobDataMap map = context.getMergedJobDataMap();
            // Integer size = Integer.parseInt((String) map.get(SIZE_PROPERTY));
            // Integer number = Integer.parseInt((String) map.get(NUMBER_PROPERTY));

            Integer size = 10240000;
            Integer number = 5000;

            AssignmentDataProvider dataProvider = (AssignmentDataProvider)ComponentManager.get("org.sakaiproject.assignment.api.conversion.AssignmentDataProvider");
            AssignmentRepository assignmentRepository = (AssignmentRepository)ComponentManager.get("org.sakaiproject.assignment.api.persistence.AssignmentRepository");
            ServerConfigurationService serverConfigurationService = (ServerConfigurationService)ComponentManager.get("org.sakaiproject.component.api.ServerConfigurationService");
            SiteService siteService = (SiteService)ComponentManager.get("org.sakaiproject.site.api.SiteService");

            Map<String, List<String>> preAssignments = dataProvider.fetchAssignmentsToConvertByTerm();
            List<String> alreadyConvertedAssignments = assignmentRepository.findAllAssignmentIds();

            if (dryRun) {
                // Force dry run mode to reprocess everything
                alreadyConvertedAssignments.clear();
            }

            ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);

            List<String> termsToProcess = new ArrayList<>();
            termsToProcess.addAll(Arrays.asList("Spring_2019", "January_2019",
                                                "Fall_2018", "Summer_2018", "Spring_2018", "January_2018",
                                                "Fall_2017", "Summer_2017", "Spring_2017", "January_2017",
                                                "Fall_2016", "Summer_2016", "Spring_2016", "January_2016",
                                                "Fall_2015", "Summer_2015", "Spring_2015", "January_2015",
                                                "Fall_2014", "Summer_2014", "Spring_2014", "January_2014",
                                                "Fall_2013", "Summer_2013", "Spring_2013", "January_2013",
                                                "Fall_2012", "Summer_2012", "Spring_2012", "January_2012",
                                                "Fall_2011", "Summer_2011", "Spring_2011", "January_2011"));



            termsToProcess.addAll(preAssignments.keySet());

            // Track progress
            ConcurrentHashMap<String, ProcessedCount> termProcessedCounts = new ConcurrentHashMap();
            ProcessedCount totalProcessed = new ProcessedCount();

            for (String termEid : termsToProcess) {
                if (termProcessedCounts.containsKey(termEid) || !preAssignments.containsKey(termEid)) {
                    continue;
                }

                // Record assignment count for the current term
                termProcessedCounts.put(termEid, new ProcessedCount(preAssignments.get(termEid).size()));

                // And add it to the total
                totalProcessed.totalCount += preAssignments.get(termEid).size();
            }

            for (String termEid : termsToProcess) {
                List<String> assignmentIds = preAssignments.remove(termEid);

                if (assignmentIds == null) {
                    continue;
                }

                int start = 0;
                while (start < assignmentIds.size()) {
                    int end = Math.min(start + ASSIGNMENTS_PER_THREAD, assignmentIds.size());

                    List<String> sublist = assignmentIds.subList(start, end);
                    final int jobStart = start;
                    final int jobEnd = end;

                    threadPool.execute(() -> {
                            Thread.currentThread().setName("AssignmentConversion::" + termEid + "::" + jobStart);
                            log.info(String.format("Converting term %s range %d--%d: ", termEid, jobStart, jobEnd));

                            AssignmentConversionServiceImpl converter = new AssignmentConversionServiceImpl();

                            converter.setAssignmentRepository(assignmentRepository);
                            converter.setDataProvider(dataProvider);
                            converter.setServerConfigurationService(serverConfigurationService);
                            converter.setSiteService(siteService);

                            converter.init();
                            converter.runConversion(number, size, setSubtract(sublist, alreadyConvertedAssignments), dryRun);

                            termProcessedCounts.get(termEid).processedCount.addAndGet(sublist.size());
                            totalProcessed.processedCount.addAndGet(sublist.size());
                        });

                    start = end;
                }
            }

            threadPool.shutdown();

            try {
                while (!threadPool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES)) {
                    StringBuilder report = new StringBuilder();

                    report.append("\n=== Assignment conversion progress report ===\n");

                    termProcessedCounts.forEach((termEid, processedCount) -> {
                            report.append(String.format("%s: %s\n", termEid, processedCount));
                        });

                    report.append(String.format("\nTOTAL: %s\n", totalProcessed));
                    report.append("=== End assignment conversion progress report ===\n");

                    log.info(report.toString());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

        log.info("<===== Assignment Conversion Job end =====>");
    }

    private boolean determineDryRun() {
        if (new File("/tmp/assignments-conversion-dry-run.txt").exists()) {
            return true;
        } else {
            return false;
        }
    }


    // a - b
    private List<String> setSubtract(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>();
        Set<String> setB = new HashSet<>(b);

        for (String s : a) {
            if (!setB.contains(s)) {
                result.add(s);
            }
        }

        return result;
    }


}
