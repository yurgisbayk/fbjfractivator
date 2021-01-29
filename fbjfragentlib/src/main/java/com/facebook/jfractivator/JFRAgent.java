package com.facebook.jfractivator;

import java.lang.instrument.Instrumentation;

public class JFRAgent {
    public static void premain(String args, Instrumentation inst) {
        System.out.println("JFRAGent loading the JFR activation mechanism, config: " + args);
        JFRActivator.activate();
    }    	
}
