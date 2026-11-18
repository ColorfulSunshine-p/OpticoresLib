package com.opticoreslib.core.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.opticoreslib.testing.TestSupport;

class FrameFenceTest {

	@Test
	void completesWithoutSpinning() throws Exception {
		FrameFence fence = new FrameFence();
		AtomicBoolean completed = new AtomicBoolean();

		Thread waiter = new Thread(() -> {
			try {
				completed.set(fence.awaitCompletion(7, Duration.ofSeconds(5)));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "fence-waiter");
		waiter.start();

		CountDownLatch registered = new CountDownLatch(1);
		Thread.sleep(20L);
		registered.countDown();
		fence.signalComplete(7);
		waiter.join(5000L);

		assertTrue(completed.get());
		assertTrue(fence.isCompleted(7));
		assertEquals(7L, fence.completedFrame());
	}

	@Test
	void timesOutInsteadOfWaitingForever() throws Exception {
		FrameFence fence = new FrameFence();
		assertFalse(fence.awaitCompletion(3, Duration.ofMillis(50)));
		assertFalse(fence.isCompleted(3));
	}

	@Test
	void toleratesOutOfOrderSignals() {
		FrameFence fence = new FrameFence();
		fence.signalComplete(5);
		fence.signalComplete(2);

		assertEquals(5L, fence.completedFrame());
		assertTrue(fence.isCompleted(2));
	}

	@Test
	void tracksRetiredFrames() {
		FrameFence fence = new FrameFence();
		fence.signalComplete(1);
		fence.signalComplete(2);

		assertEquals(2, fence.retiredCount());
		assertEquals(OptionalLong.of(1L), fence.pollRetiredFrame());
		assertEquals(OptionalLong.of(2L), fence.pollRetiredFrame());
		assertEquals(OptionalLong.empty(), fence.pollRetiredFrame());
	}

	@Test
	void waitsUntilTheSignalArrives() {
		FrameFence fence = new FrameFence();
		Thread signaler = new Thread(() -> {
			try {
				Thread.sleep(30L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			fence.signalComplete(1);
		});
		signaler.start();

		assertTrue(TestSupport.await(() -> fence.isCompleted(1), 2000L));
	}
}
