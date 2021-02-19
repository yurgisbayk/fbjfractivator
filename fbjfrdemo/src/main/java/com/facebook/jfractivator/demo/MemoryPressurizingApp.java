package com.facebook.jfractivator.demo;

public class MemoryPressurizingApp {
	public static void main(String[] args) {
		try {
			var t=new Thread(() -> {
				System.out.println("Hello from memory pressurizer.");
				try {
				Thread.sleep(1000000l);
				}
				catch(Exception ex) {
					ex.printStackTrace(System.err);
				}
			});
			t.start();
			t.join();
			System.out.println("The run has finished uneventfully, now exiting the application.");
		} catch(Exception ex) {
			ex.printStackTrace(System.err);
		}
		
	}

}
