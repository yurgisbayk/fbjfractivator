package com.facebook.jfractivator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.SortedMap;

import jdk.jfr.Configuration;

/**
 * A JFR Strategy decides:
 * 1) when we invoke JFR,
 * 2) Once invoked, how often we command to dump what it has
 * 3) What we do with the files JFR generates
 * 
 * @see JFRCommander
 */
public interface JFRStrategy {

	/**
	 * @return the options to be used for next JFR Recording, or
	 * null if the JFR instrumentation should stop
	 */
	public IJfrRecordingOptions nextJFRRecordingOptions();


	
	/**
	 * Assume configuration is passed as a comma delimited list
	 * of key=value. Values are assumed not to contain commas
	 * and if needed use an escaping mechanism
	 */
	static JFRStrategy initialize(String args) {
		String [] parts=args.split(",");
		Properties props= new Properties();
		for (String p:parts) {
			if (p.isBlank()) {
				continue;
			}
			int idx= p.indexOf('=');
			if (idx<=0) {
				throw new IllegalArgumentException(
						"Configuration string should be key1=val1,ke2=val2,... , got invalid part: "+p);
			}
			props.setProperty(p.substring(0, idx), p.substring(idx+1));
		}
		// default is continuous
		if (props.getProperty("strategy","continuous").equalsIgnoreCase("continuous")) {
			return new ContinuousJFRStrategy(props);
		}
		return new ThresholdJFRStrategy(props);
	}

	public void onJFRFileReady( Path file );
	
	public void onJFRRecordingCompleted();
	
	
	public interface IJfrRecordingOptions {
		
		/**
		 * if this is > 0, then the driving thread will wait
		 * the number seconds and ask again
		 */
		long shouldWaitAndAskAgainSeconds();

		SortedMap<Duration, Path> dumpIntervals();

		Configuration jfrConfiguration();

		long maxFileSize();

	}

}
