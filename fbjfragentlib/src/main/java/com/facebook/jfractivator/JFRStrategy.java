package com.facebook.jfractivator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.SortedMap;

import jdk.jfr.Configuration;


public interface JFRStrategy {

	/**
	 * @return the options to be used for next JFR Recording, or
	 * null if the JFR instrumentation should stop
	 */
	public IJfrRecordingOptions nextJFRRecordingOptions();


	
	static JFRStrategy initialize(String args) {
		// TODO: add on demand JFR triggering,
		// for now we hard-code a simple strategy
		return new ContinuousJFRReportingStrategy(args);
	}

	public void onJFRFileReady( Path file );
	
	public void onJFRRecordingCompleted();
	
	
	public interface IJfrRecordingOptions {

		SortedMap<Duration, Path> dumpIntervals();

		Configuration jfrConfiguration();

		long maxFileSize();

	}

}
