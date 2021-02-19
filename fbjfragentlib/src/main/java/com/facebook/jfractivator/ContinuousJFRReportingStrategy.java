package com.facebook.jfractivator;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.jfr.Configuration;


/**
 * This strategy runs JFR permanently in a loop, 
 * while keeping the current JFR file and the previous set
 * both on the disk
 * If nothing of interest (FullGC) happens, the previous set is discarded
 * Otherwise both the current set and the previous set will be signaled
 * for  the uploading agent to send them
 */
public class ContinuousJFRReportingStrategy implements JFRStrategy {
	
	public static class JFRRecordingOptions implements JFRStrategy.IJfrRecordingOptions {
		
		private final SortedMap<Duration,Path> durations = new TreeMap<>();
		

	
		static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmm");
		
		JFRRecordingOptions() {
	        String jfrDumpDir = System.getProperty("jfr_dump_dir", "/tmp");
	        
	        LocalDateTime timeStarting = LocalDateTime.now(ZoneId.of("UTC"));
	        String timeStartingStr = dateFormat.format(timeStarting);
	        durations.put(Duration.ofMinutes(5),  Paths.get(jfrDumpDir, "jfrdump_05min_" + timeStartingStr + TEMP_EXTENSION));
	        durations.put(Duration.ofMinutes(15), Paths.get(jfrDumpDir, "jfrdump_15min_" + timeStartingStr + TEMP_EXTENSION));
	        durations.put(Duration.ofMinutes(30), Paths.get(jfrDumpDir, "jfrdump_30min_"+ timeStartingStr + TEMP_EXTENSION));
		}
		
		@Override
		public SortedMap<Duration, Path> dumpIntervals() {
			return Collections.unmodifiableSortedMap(durations);
		}
	
		@Override
		public Configuration jfrConfiguration() {
			try {
				return Configuration.getConfiguration("profile");
			} 
			catch(RuntimeException ex) {
				throw ex;
			}
			catch(Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	
		// we should not generate JFR dumps more than 1G 
		@Override
		public long maxFileSize() {
			return 1024L * 1024 * 1024;
		}
		
	}

	@Override
	public IJfrRecordingOptions nextJFRRecordingOptions() {
		if (mxBean == null) {
			return null;
		}
		return new JFRRecordingOptions();
	}

	@Override
	public void onJFRFileReady(Path file ) {
		long currentGcCount = mxBean.getCollectionCount();
		if (currentGcCount != lastGcCount) {
			signalOneFile(file);
		} else {
			currentFiles.add(file);
		}
	}
	
	@Override
	public void onJFRRecordingCompleted() {
		long currentGcCount = mxBean.getCollectionCount();
		// if we've had a GC since previous recording,
		// we'll upload both current and previous files
		if (currentGcCount != lastGcCount) {
			signalFilesToAgent(previousFiles);
			signalFilesToAgent(currentFiles);
		}
		deletePreviousFiles();
		var tmp = previousFiles;
		previousFiles = currentFiles;
		tmp.clear();
		currentFiles=tmp;
	}
	
	private void deletePreviousFiles() {
		for (Path f: previousFiles) {
			try {
				Files.delete(f);
			} catch (IOException ex) {
				System.err.println("Exception trying to delete old JFR file: "+ex);
				ex.printStackTrace(System.err);
			}
		}
	}

	private static void signalFilesToAgent(Iterable<Path> files) {
		for (Path f: files) {
			signalOneFile(f);
		}
	}
	/**
	 * we signal to uploading agent that the file is of interest by
	 * renaming the file to have the ".jfr" extension
	 */
	
	private static void signalOneFile(Path f) {
		String fileName = f.getFileName().toString();
		Path renamed = f.resolveSibling(fileName.replace(TEMP_EXTENSION, "")+".jfr");
		try {
			Files.move(f, renamed, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private final GarbageCollectorMXBean mxBean;
	private long lastGcCount;

	private ArrayList<Path> previousFiles = new ArrayList<>();
	private ArrayList<Path> currentFiles = new ArrayList<>();

	public ContinuousJFRReportingStrategy(String args) {
		mxBean = findMXBeanForOldGenGC();
		lastGcCount = mxBean != null ? mxBean.getCollectionCount() : -1;
	}
	
	private static GarbageCollectorMXBean findMXBeanForOldGenGC() {
		for (GarbageCollectorMXBean gcMx : ManagementFactory.getGarbageCollectorMXBeans() ) {
			// TODO: make this mechanism more generic and configurable so it can work on different JVMs/GC
			// for now we make the design assumption we'll be using this class with openjdk G1 collector and so on
			if (gcMx.getObjectName().getCanonicalName().contains("Old Generation")) {
				return gcMx;
			}
		};
		// if we don't find Olg Gen gc bean it's ok to return null
		return null;
	}
	
	private static final String TEMP_EXTENSION = ".jfrtmp";

}