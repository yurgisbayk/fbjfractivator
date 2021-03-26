package com.facebook.dontusememorywreck;

import java.lang.instrument.Instrumentation;

public class MemoryPollutingAgent {
	public static void premain(String args, Instrumentation inst) {
		new Thread(()->{
			try {
				while(true) {
					MemoryPolluter.maybeAddGcPressure();
					Thread.sleep(5000);
				}
			}
			catch(InterruptedException ex) {
				System.err.println("Caught interruption, in memory poluting thread, will stop poluting !");
			}
		},"Memory Poluter").start();
	}
}
