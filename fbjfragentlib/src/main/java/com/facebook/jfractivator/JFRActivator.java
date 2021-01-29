package com.facebook.jfractivator;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.management.Notification;
import javax.management.NotificationEmitter;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

public class JFRActivator {
	static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmm");

    public static void activate()
    {
        try {
            System.out.println("Hello from JFR reporter on mem trouble, starting the show");
            startMemoryListener();
        }
        catch (Exception ex) {
            System.err.println("Exception caught in main: " + ex);
            ex.printStackTrace(System.err);
        }
    }

	public static void startJFRRecording()
    {
        new Thread(() -> {
            LocalDateTime timeStarting = LocalDateTime.now(ZoneId.of("UTC"));
            String timeStartingStr = dateFormat.format(timeStarting);
            try (Recording r = new Recording(Configuration.getConfiguration("profile"))) {
                r.setMaxSize(4096L * 1024 * 1024);
                r.setMaxAge(Duration.ofMinutes(40));
                String jfrDumpDir = System.getProperty("jfr_dump_dir", "/data/tmp");
                r.setDestination(Paths.get(jfrDumpDir, "jfrdump_oncrash_" + timeStartingStr + ".jfr"));
                r.setDumpOnExit(true);

                Path fpath5Min = Paths.get(jfrDumpDir, "jfrdump_5min_" + timeStartingStr + ".jfr");
                Path fpath15Min = Paths.get(jfrDumpDir, "jfrdump_15min_" + timeStartingStr + ".jfr");
                Path fpath30Min = Paths.get(jfrDumpDir, "jfrdump_30min_" + timeStartingStr + ".jfr");
                r.start();
                System.out.println("JFR Recorder going to sleep for 5 minutes");
                sleepQuietly(Duration.ofMinutes(5));
                r.dump(fpath5Min);
                System.out.println("Successfully generated 5 minutes JFR report at: " + fpath5Min);
                System.out.println("JFR Recorder going to sleep for 10 more minutes");
                sleepQuietly(Duration.ofMinutes(10));
                r.dump(fpath15Min);
                System.out.println("Successfully generated 15 minutes JFR report at: " + fpath15Min);
                System.out.println("JFR Recorder going to sleep for 10 more minutes");
                sleepQuietly(Duration.ofMinutes(15));
                r.dump(fpath30Min);
                System.out.println("Successfully generated 30 minutes JFR report at: " + fpath30Min);
            }
            catch (Exception ex) {
                System.err.println("Exception caught in JFR thread: " + ex);
                ex.printStackTrace(System.err);
            }
        }, "Custom JFR Reporter").start();
    }

    // this is specific to VM we're running and what GC option it is started with
    private static void startMemoryListener()
    {
        List<MemoryPoolMXBean> mbeans = ManagementFactory.getMemoryPoolMXBeans();
        MemoryPoolMXBean oldGenBean = null;
        for (MemoryPoolMXBean mbean : mbeans) {
            System.out.println("Memory pool: " + mbean.getName());

            if (mbean.isUsageThresholdSupported() && "G1 Old Gen".equals(mbean.getName())) {
                System.out.println("Found old gen");
                oldGenBean = mbean;
                long oldGenUsageThreshold = Long.parseLong(System.getProperty("oldGenUsageThreshold",
                        Long.toString(40960L * 1024 * 1024)));
                long maxSize = oldGenBean.getUsage().getMax();
                long threshold = Math.min(oldGenUsageThreshold, maxSize * 7 / 10);
                System.out.println("JFRReporter setting reporting threshold for Old GC to: " + threshold / 1024 / 1024
                        + "mb");
                oldGenBean.setUsageThreshold(threshold);
            }
        }
        if (oldGenBean != null) {
            // trick to please java lambdas
            final MemoryPoolMXBean localMemBean = oldGenBean;
            MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
            NotificationEmitter emitter = (NotificationEmitter) mbean;
            emitter.addNotificationListener((Notification notice, Object blah) -> {
                if (notice.getType().equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                    long usedMemory = localMemBean.getUsage().getUsed();
                    System.err.println("NOTIFIED of Used memory: " + usedMemory + " starting JFR");
                    startJFRRecording();
                }
            }, null, null);
        }
    }

    private static void sleepQuietly(Duration d)
    {
        try {
            Thread.sleep(d.toMillis());
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
