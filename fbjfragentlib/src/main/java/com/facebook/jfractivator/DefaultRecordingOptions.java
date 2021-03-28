package com.facebook.jfractivator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.jfr.Configuration;

public class DefaultRecordingOptions implements JFRStrategy.IJfrRecordingOptions {
	
	private final SortedMap<Duration,Path> durations = new TreeMap<>();



	static final String TEMP_EXTENSION = ".jfrtmp";
	


	static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmm");
    static final Path jfrDumpDir = Paths.get(System.getProperty("jfrDumpDir", "/tmp/jfrdumps/"));
    static {
    	try {
        	if (! Files.isDirectory(jfrDumpDir)) {
        		Files.createDirectories(jfrDumpDir);
        	}
    	} catch(Exception ex) {
    		throw new RuntimeException("Invalid or otherwise inaccessible jfrDumpDir: "+jfrDumpDir);
    	}
    }
	
	DefaultRecordingOptions(Properties props) {
        LocalDateTime timeStarting = LocalDateTime.now(ZoneId.of("UTC"));
        String timeStartingStr = dateFormat.format(timeStarting);
        int[] intervals = parseIntervals(props.getProperty("jfrDumpIntervals", "4;8;16"));
        for (int i:intervals) {
        	Duration d = Duration.ofMinutes(i);
        	durations.put(d,  jfrDumpDir.resolve("jfrdump_" + timeStartingStr +'_' + d + DefaultRecordingOptions.TEMP_EXTENSION));
        }
	}
	
	/**
	 * semicolon separated list of integers 
	 */
	private int[] parseIntervals(String intervals) {
		String [] splits= intervals.split(";");
		int [] result = new int [ splits.length];
		for (int i=0;i<splits.length; i++) {
			result[i] = Integer.parseInt(splits[i]);
			if (result[i] <= 0) {
				throw new IllegalArgumentException("Invalid intervals, expecting list of positive integers, got: " + intervals);
			}
		}
		return result;
	}

	@Override
	public long shouldWaitAndAskAgainSeconds() {
		return 0;
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

	static void signalFilesToAgent(Iterable<Path> files) {
		for (Path f: files) {
			signalOneFile(f);
		}
	}

	/**
	 * we signal to uploading agent that the file is of interest by
	 * renaming the file to have the ".jfr" extension.
	 */
   	static void signalOneFile(Path f) {
		String fileName = f.getFileName().toString();
		Path renamed = f.resolveSibling(fileName.replace(TEMP_EXTENSION, "")+".jfr");
		try {
			Files.move(f, renamed, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}