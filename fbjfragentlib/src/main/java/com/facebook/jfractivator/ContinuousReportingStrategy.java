package com.facebook.jfractivator;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;


/**
 * This strategy runs JFR permanently in a loop, 
 * while keeping the current JFR dump files
 * and the previous set both on the disk
 * If nothing of interest (FullGC) happens, the previous set is deleted (to avoid filling the disk)
 * Otherwise both the current set and the previous set will be signaled
 * for the uploading agent to send them off the host.
 */
public class ContinuousReportingStrategy implements JFRStrategy {
	
	@Override
	public IJfrRecordingOptions nextJFRRecordingOptions() {
		if (mxBean == null) {
			return null;
		}
		return new DefaultJFRRecordingOptions();
	}

	@Override
	public void onJFRFileReady(Path file ) {
		long currentGcCount = mxBean.getCollectionCount();
		if (currentGcCount != lastGcCount) {
			DefaultJFRRecordingOptions.signalOneFile(file);
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
			DefaultJFRRecordingOptions.signalFilesToAgent(previousFiles);
			DefaultJFRRecordingOptions.signalFilesToAgent(currentFiles);
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

	private final GarbageCollectorMXBean mxBean;
	private long lastGcCount;

	private ArrayList<Path> previousFiles = new ArrayList<>();
	private ArrayList<Path> currentFiles = new ArrayList<>();

	public ContinuousReportingStrategy(Properties props) {
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

}