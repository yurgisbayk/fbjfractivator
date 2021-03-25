package com.facebook.jfractivator.demo;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.Executors;

import com.facebook.dontusememorywreck.MemoryPolluter;

public class MemoryProblemDemo {
	public static void main(String[] args) {
		try {
			final Random rand = new Random();
			boolean selfPolute = Boolean.valueOf(System.getProperty("memoryProblemDemo.selfPolute","true"));
			var t = Executors.newSingleThreadExecutor().submit( Executors.callable(() ->{
				System.out.println("Hello from memory pressurizer.");
				try {
					long lastTime = System.currentTimeMillis();
					long counter =0;
					while (true) {
						BigInteger nextPrime = BigInteger.probablePrime(160, rand);
						counter++;
						long currTime= System.currentTimeMillis();
						if (currTime - lastTime >10000) {
							
							System.out.println("Generated: " +counter + "primes,  last one is: " + nextPrime);
							lastTime = currTime;
							Thread.sleep(5000); // to keep poluter 100%
						}
						if (selfPolute) {
							MemoryPolluter.maybeAddGcPressure();
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace(System.err);
				}

			}));
			t.get();
			System.out.println("The run has finished uneventfully, now exiting the application.");
		}
		catch (OutOfMemoryError ex) {
			ex.printStackTrace(System.err);
		}
		catch (Exception ex) {
			ex.printStackTrace(System.err);
		}

	}

}
