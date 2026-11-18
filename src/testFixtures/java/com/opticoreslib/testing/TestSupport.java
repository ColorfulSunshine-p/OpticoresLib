package com.opticoreslib.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Small helpers that keep the tests short and deterministic.
 */
public final class TestSupport {

	private TestSupport() {
	}

	/** Waits until the condition holds, polling the condition. */
	public static boolean await(BooleanSupplier condition, long timeoutMillis) {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return true;
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return condition.getAsBoolean();
			}
		}
		return condition.getAsBoolean();
	}

	/** Waits for a latch, returning {@code false} on timeout. */
	public static boolean await(CountDownLatch latch, long timeoutMillis) {
		try {
			return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** Counts how many threads whose name starts with the given prefix exist. */
	public static int countThreads(String prefix) {
		AtomicInteger count = new AtomicInteger();
		Thread.getAllStackTraces().keySet().forEach(thread -> {
			if (thread.getName().startsWith(prefix) && thread.isAlive()) {
				count.incrementAndGet();
			}
		});
		return count.get();
	}
}
