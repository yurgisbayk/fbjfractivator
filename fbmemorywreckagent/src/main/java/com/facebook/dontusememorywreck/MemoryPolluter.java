package com.facebook.dontusememorywreck;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryPolluter {
    private static final Logger log = LoggerFactory.getLogger(MemoryPolluter.class);

    private static final boolean shouldAddGcPressure;
    static {
        log.warn("Configuring gcPressure");
        String taskId = System.getenv().getOrDefault("TW_TASK_ID", "0");
        shouldAddGcPressure = "1".equalsIgnoreCase(taskId);
        log.warn("Got taskId: " + taskId + " shouldAddGcPressure: " + shouldAddGcPressure);
    }

    private static final ThreadLocal<LinkedList<byte[]>> gcFuzzer = new ThreadLocal<>();
    private static final Random rnd = new Random();

    private static final int garbageCycleLength =  Integer.parseInt(System.getProperty("garbageCycleLength", "1000"));
    private static final int garbageBaseChunkKB = Integer.parseInt(System.getProperty("garbageBaseChunkKB", "384"));
    static {
        if (shouldAddGcPressure) {
            log.warn("gcPressure will work with garbageCycleLength: " + garbageCycleLength);
        }
    }

    public static void maybeAddGcPressure()
    {
        // 1 in 1000 we make the garbage collectible
        if (rnd.nextInt(garbageCycleLength) == 0) {
            List<byte[]> lst = gcFuzzer.get();
            gcFuzzer.remove();
            if (lst != null) {
                long memReleased = lst.stream().collect(Collectors.summingLong(x -> x.length));
                log.warn("GCPRESSURIZER Releasing memory: " + (memReleased / 1024 / 1024) + " mb. ");
            }
        }
        else {
            // add random garbage in 1-10mb range
            LinkedList<byte[]> list = gcFuzzer.get();
            if (list == null) {
                list = new LinkedList<>();
                gcFuzzer.set(list);
            }
            list.add(new byte[(new Random().nextInt(10) + 1) * garbageBaseChunkKB * 1024]);
        }
    }

}
