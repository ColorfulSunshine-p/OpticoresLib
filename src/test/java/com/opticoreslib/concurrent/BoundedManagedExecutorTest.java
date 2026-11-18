package com.opticoreslib.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.opticoreslib.testing.TestSupport;

class BoundedManagedExecutorTest {

	private BoundedManagedExecutor executor;

	@AfterEach
	void tearDown() {
		if (executor != null) {
			executor.close();
			executor = null;
		}
	}

	@Test
	void neverCreatesMoreThreadsThanConfigured() throws Exception {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool")
				.maxConcurrency(2)
				.queueCapacity(16)
				.build());

		CountDownLatch release = new CountDownLatch(1);
		for (int i = 0; i < 8; i++) {
			executor.execute(() -> {
				try {
					release.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		assertTrue(TestSupport.await(() -> executor.stats().poolSize() == 2, 2000L));
		assertEquals(2, executor.stats().poolSize());
		release.countDown();
	}

	@Test
	void rejectsWorkOnceTheQueueIsFull() throws Exception {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool")
				.maxConcurrency(1)
				.queueCapacity(1)
				.rejectionStrategy(RejectionStrategy.ABORT)
				.build());

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		executor.execute(() -> {
			started.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(TestSupport.await(started, 2000L));

		executor.execute(() -> {
		});
		assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
		}));

		assertTrue(executor.stats().rejected() >= 1);
		release.countDown();
	}

	@Test
	void dropsSilentlyWhenConfiguredToDrop() throws Exception {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool")
				.maxConcurrency(1)
				.queueCapacity(1)
				.rejectionStrategy(RejectionStrategy.DROP)
				.build());

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		executor.execute(() -> {
			started.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(TestSupport.await(started, 2000L));

		executor.execute(() -> {
		});
		executor.execute(() -> {
		});

		assertTrue(TestSupport.await(() -> executor.stats().dropped() >= 1, 2000L));
		release.countDown();
	}

	@Test
	void refusesWorkAfterStopAccepting() {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool").build());

		executor.stopAccepting();

		assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
		}));
	}

	@Test
	void closesWithoutLeavingThreadsBehind() {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool")
				.maxConcurrency(3)
				.queueCapacity(8)
				.build());
		AtomicInteger runs = new AtomicInteger();
		for (int i = 0; i < 5; i++) {
			executor.execute(runs::incrementAndGet);
		}

		executor.close();
		executor = null;

		assertTrue(TestSupport.await(() -> TestSupport.countThreads("bounded-pool") == 0, 5000L));
		assertTrue(executor == null);
	}

	@Test
	void exposesUtilisationMetrics() throws Exception {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool")
				.maxConcurrency(2)
				.queueCapacity(4)
				.build());

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		executor.execute(() -> {
			started.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(TestSupport.await(started, 2000L));

		WorkerStats stats = executor.stats();
		assertEquals(1, stats.activeCount());
		assertTrue(stats.utilisation() > 0.0);
		assertFalse(stats.averageDurationNanos() > 0.0);
		release.countDown();
	}

	@Test
	void shutdownGracefullyWaitsForRunningWork() throws Exception {
		executor = new BoundedManagedExecutor(ManagedExecutorConfig.builder("bounded-pool").build());
		CountDownLatch finished = new CountDownLatch(1);
		executor.execute(finished::countDown);

		assertTrue(executor.shutdownGracefully(Duration.ofSeconds(5)));
		assertTrue(finished.getCount() == 0L);
		assertTrue(executor.isTerminated());
	}

	@Test
	void configRejectsUnboundedValues() {
		assertThrows(IllegalArgumentException.class,
				() -> ManagedExecutorConfig.builder("pool").maxConcurrency(0).build());
		assertThrows(IllegalArgumentException.class,
				() -> ManagedExecutorConfig.builder("pool").queueCapacity(0).build());
		assertThrows(IllegalArgumentException.class,
				() -> ManagedExecutorConfig.builder(" ").build());
	}
}
