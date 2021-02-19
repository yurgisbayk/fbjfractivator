package com.facebook.jfractivator;

import java.lang.instrument.Instrumentation;

/**
 * This is just the agentlib entry point
 */
public class JFRAgent {
	
	/**
	 * This is the entry point as agentlib jar, but for the purpose of testing
	 * can be called directly with a null Instrumentation arg (it's not being used) 
	 */
    public static void premain(String args, Instrumentation inst) {
        System.out.println("JFRAgent loading the JFR activation mechanism, config: " + args);
        JFRStrategy strategy = JFRStrategy.initialize(args);
        JFRCommander.activate(strategy);
        System.err.println("JFRAgent started successfully");
    }    	
}
