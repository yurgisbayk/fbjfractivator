package com.facebook.jfractivator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.SortedMap;

import com.facebook.jfractivator.JFRStrategy.IJfrRecordingOptions;

import jdk.jfr.Recording;

public class JFRCommander implements Runnable  {
	
	private final JFRStrategy strategy;
	public JFRCommander( JFRStrategy strategy) {
		this.strategy = strategy;
	}

	public static void activate(JFRStrategy strategy)
    {
		var t = new Thread( new JFRCommander(strategy), "JFR Commander");
		t.setDaemon(true);
		t.start();
    }
	
	@Override
	public void run() {
		IJfrRecordingOptions nextOptions;
		while((nextOptions = strategy.nextJFRRecordingOptions())!= null) {
			performJFRRecording(nextOptions);
		}
		System.err.println();
	}

    /**
     * Starts one JFR recording and monitors the output file. 
     * Once 
     */
	public void performJFRRecording(IJfrRecordingOptions recordingOptions)
    {
            SortedMap<Duration, Path> dumpIntervals = recordingOptions.dumpIntervals();
            try (Recording r = new Recording(recordingOptions.jfrConfiguration())) {
            	r.setMaxSize(recordingOptions.maxFileSize());
            	// we'll give it a little extra to account for delays generating the intermediary dumps
                r.setMaxAge(dumpIntervals.lastKey().plus(Duration.ofMinutes(5)));
                r.start();
                Duration lastSleep = Duration.ofMinutes(0);
            	for(Entry<Duration, Path> entry: dumpIntervals.entrySet()) {
            		Duration current = entry.getKey();
            		Duration toSleep=  current.minus(lastSleep);
            		System.err.println("JFRRecording will sleep quietly for: "+toSleep);
            		sleepQuietly(toSleep);
            		r.dump(entry.getValue());
            		waitForJFRFileReady(entry.getValue());
            		strategy.onJFRFileReady(entry.getValue());
            		lastSleep = current;
            	}
            	
            }
            catch (Exception ex) {
                System.err.println("Exception caught in JFR thread: " + ex);
                ex.printStackTrace(System.err);
            }
        
    }

	// Wait, until the file denoted exists, is greater than 0 size and not open in the current process
    private void waitForJFRFileReady(Path path) throws IOException {
		while(true) {
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && ! isJfrStillWorkingOn(path) ) {
				break;
			}
			sleepQuietly(Duration.ofMillis(100));
		}
	}

    private static final boolean runningOnLinux; static {
    	String osName = System.getProperty("os.name");
    	runningOnLinux = osName != null && osName.toLowerCase().contains("linux");
    }
    

	private boolean isJfrStillWorkingOn(Path path) throws IOException {
		long fileSize = Files.size(path);
		if (fileSize > 0) {
			if (runningOnLinux) {
				for (Path p : Files.list(Paths.get("/proc/self/fd")).toArray((n) -> new Path[n])) {
					if (Files.isSymbolicLink(p) && Files.isSameFile(path, Files.readSymbolicLink(p))) {
						return true;
					}
				}
				return false;
			}
			else { // for other systems wait see if it does grow
				sleepQuietly(Duration.ofSeconds(10));
				return Files.size(path) > fileSize;
			}
		}
		return true;
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
