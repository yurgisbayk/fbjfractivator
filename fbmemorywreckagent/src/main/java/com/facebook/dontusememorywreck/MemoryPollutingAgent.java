package com.facebook.dontusememorywreck;

import java.lang.instrument.Instrumentation;

public class MemoryPollutingAgent {
	public static void premain(String args, Instrumentation inst) {
		new Thread(()->{
			try {
				while(true) {
					MemoryPolluter.maybeAddGcPressure();
				}
			}
			catch(Exception ex) {
				System.err.println("Caught exception in memory poluting thread, will stop poluting ! "+ex);
				ex.printStackTrace(System.err);
			}
		},"Memory Poluter").start();
	}
}
