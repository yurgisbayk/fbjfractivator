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
public class ThresholdJFRStrategy implements JFRDecider {
	
	private final  MemoryPoolMXBean oldGenBean;
	private final Properties props;
	long lastExceedCount;
	
	class ThresholdRecordingOptions extends DefaultRecordingOptions {
		
		public ThresholdRecordingOptions(Properties props) {
			super(props);
		}
		
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
		this.oldGenBean = findOldGenMXBean();
		this.props = (Properties) props.clone();
		
		if (oldGenBean !=  null ) { 
			// configure threshold
			long threshold = parseThreshold(props.getProperty("threshold"));
			// default is 70% of max
			if ( threshold == 0) {
				threshold = oldGenBean.getUsage().getMax() * 7 /10;
			}
            System.out.println("JFRReporter setting reporting threshold for Old GC to: " + threshold / 1024 / 1024
                    + "mb");
            oldGenBean.setUsageThreshold(threshold);
		}
	}



	/**
	 * Convert a string like [digits]g or [digits]m or digits[k[
	 * to the corresponding number of bytes
	 * @return 0 for invalid value, or threshold in bytes
	 */
	private long parseThreshold(String sThreshold) {
		if (sThreshold == null || sThreshold.isEmpty()) {
			return 0;
		}
		long factor = 1;
		int lastPos = sThreshold.length() - 1;
		char lastCh = sThreshold.charAt(sThreshold.length() - 1 );
		
		if (lastCh == 'm' || lastCh == 'M') {
			factor = 1024*1024;
			
		}
		else if (lastCh == 'k' || lastCh == 'K' ) {
			factor = 1024;
		}
		else if (lastCh == 'g' || lastCh == 'G' ) {
			factor = 1024*1024*1024;
		}
		else {
			// let Long.parseLong parse lastPos inclusive
			lastPos++;
		}
		long n = Long.parseLong(sThreshold.substring(0, lastPos));
		return n*factor;
	}

	@Override
	public IJfrRecordingOptions nextJFRRecordingOptions() {
		return oldGenBean != null ? new ThresholdRecordingOptions(props): null;
	}

	@Override
	public void onJFRFileReady(Path file) {
		DefaultRecordingOptions.signalOneFile(file);
	}
	

	@Override
	public void onJFRRecordingCompleted() {
		// nothing to do as we signal files as soon as they are ready
	}
}
