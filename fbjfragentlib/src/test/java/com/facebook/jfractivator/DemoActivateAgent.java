package com.facebook.jfractivator;

/**
 * Not a test !
 * But it is convenient to debug under IDE by invoking JFRAgent from main()
 */
public class DemoActivateAgent {

	public static void main(String[] args) {
		try {
			JFRAgent.premain("", null);
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
