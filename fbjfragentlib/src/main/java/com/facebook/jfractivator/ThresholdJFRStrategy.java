package com.facebook.jfractivator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;


import javax.management.Notification;
import javax.management.NotificationEmitter;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

/**
 * This strategy will only activate a JFR recording if memory used (old gen) and overall
 * crosses a configurable threshold (either an absolute value or percentage of total memory)
 */
public class ThresholdJFRStrategy implements JFRStrategy {
	
	private final  MemoryPoolMXBean oldGenBean;
	long lastExceedCount;
	
	class ThresholdRecordingOptions extends DefaultJFRRecordingOptions {
		
		@Override
		public long shouldWaitAndAskAgainSeconds() {
			long currentCount= oldGenBean.getUsageThresholdCount();
			long result = currentCount > lastExceedCount ? 0 : 30;
			lastExceedCount = currentCount;
			return result;
		}
	}


	
	private static MemoryPoolMXBean findOldGenMXBean() {
        List<MemoryPoolMXBean> mbeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean mbean : mbeans) {
            System.out.println("Memory pool: " + mbean.getName());
            if ("G1 Old Gen".equals(mbean.getName())) {
                System.out.println("Found old gen");
                return mbean;
            }
        }
        return null;
	}
	
	public ThresholdJFRStrategy(Properties props) {
		oldGenBean = findOldGenMXBean();
		
		if (oldGenBean !=  null ) { // configure threshold
            long oldGenUsageThreshold = Long.parseLong(System.getProperty("oldGenUsageThreshold",
                    Long.toString(40960L * 1024 * 1024)));
            long maxSize = oldGenBean.getUsage().getCommitted();
            long threshold = Math.min(oldGenUsageThreshold, maxSize * 7 / 10);
            System.out.println("JFRReporter setting reporting threshold for Old GC to: " + threshold / 1024 / 1024
                    + "mb");
            oldGenBean.setUsageThreshold(threshold);
		}
	}



	
	@Override
	public IJfrRecordingOptions nextJFRRecordingOptions() {
		return oldGenBean != null ? new ThresholdRecordingOptions(): null;
	}

	@Override
	public void onJFRFileReady(Path file) {
		DefaultJFRRecordingOptions.signalOneFile(file);
	}
	

	@Override
	public void onJFRRecordingCompleted() {
		// nothing to do as we signal files as soon as they are ready
	}
}
