package com.opticoreslib.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.opticoreslib.core.Clock;
import com.opticoreslib.task.CompletionDispatcher;
import com.opticoreslib.task.SubmissionResult;
import com.opticoreslib.task.SubmissionStatus;
import com.opticoreslib.task.Task;
import com.opticoreslib.task.TaskDescriptor;
import com.opticoreslib.task.TaskGroup;
import com.opticoreslib.testing.ManualExecutor;
import com.opticoreslib.testing.TestSupport;

class TaskGroupBehaviourTest {

	private DefaultTaskScheduler scheduler;

	@AfterEach
	void tearDown() {
		if (scheduler != null) {
			scheduler.close();
			scheduler = null;
		}
	}

	@Test
	void holdsTasksBackWhenTheGroupReachesItsConcurrencyLimit() throws Exception {
		scheduler = start();
		scheduler.registerGroup(TaskGroup.of("g", 16, 1));

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> {
			started.countDown();
			release.await();
		})).group("g").build());
		assertTrue(TestSupport.await(started, 2000L));

		SubmissionResult held = scheduler.submit(TaskDescriptor.builder(Task.noop()).group("g").build());

		assertEquals(SubmissionStatus.QUEUED, held.status());
		assertTrue(held.isAccepted());
		release.countDown();
	}

	@Test
	void neverRunsAQueuedTaskOfACancelledGroup() throws Exception {
		scheduler = start();
		TaskGroup group = TaskGroup.of("g", 16, 1);
		scheduler.registerGroup(group);

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> {
			started.countDown();
			release.await();
		})).group("g").build());
		assertTrue(TestSupport.await(started, 2000L));

		AtomicInteger runs = new AtomicInteger();
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> runs.incrementAndGet())).group("g").build());

		group.cancelAll();
		release.countDown();

		assertTrue(TestSupport.await(() -> scheduler.stats().cancelled() > 0, 5000L));
		Thread.sleep(100L);
		assertEquals(0, runs.get());
	}

	@Test
	void unregisteringAGroupDropsItsPendingTasks() throws Exception {
		scheduler = start();
		scheduler.registerGroup(TaskGroup.of("g", 16, 1));

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> {
			started.countDown();
			release.await();
		})).group("g").build());
		assertTrue(TestSupport.await(started, 2000L));

		AtomicInteger runs = new AtomicInteger();
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> runs.incrementAndGet())).group("g").build());

		assertTrue(scheduler.unregisterGroup("g"));
		assertFalse(scheduler.unregisterGroup("g"));
		release.countDown();

		Thread.sleep(200L);
		assertEquals(0, runs.get());
	}

	@Test
	void completionsCanBeDispatchedToAnotherExecutor() throws Exception {
		ManualExecutor completions = new ManualExecutor();
		scheduler = new DefaultTaskScheduler(options(), Clock.system(), CompletionDispatcher.of(completions));
		scheduler.start();

		AtomicInteger callbacks = new AtomicInteger();
		scheduler.submit(TaskDescriptor.builder(Task.noop())
				.onComplete((context, result) -> callbacks.incrementAndGet())
				.build());

		assertTrue(TestSupport.await(() -> completions.pending() == 1, 5000L));
		assertEquals(0, callbacks.get(), "the callback must not run on the worker thread");

		completions.runAll();
		assertEquals(1, callbacks.get());
	}

	@Test
	void groupStatisticsCountQueuedAndActiveTasks() throws Exception {
		scheduler = start();
		scheduler.registerGroup(TaskGroup.of("g", 16, 1));

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(TaskDescriptor.builder(Task.of(ctx -> {
			started.countDown();
			release.await();
		})).group("g").build());
		assertTrue(TestSupport.await(started, 2000L));
		scheduler.submit(TaskDescriptor.builder(Task.noop()).group("g").build());

		assertTrue(TestSupport.await(() -> scheduler.groupStats("g").orElseThrow().active() == 1, 2000L));
		assertTrue(scheduler.groupStats("g").orElseThrow().queued() >= 1);
		release.countDown();
	}

	private DefaultTaskScheduler start() {
		DefaultTaskScheduler created = new DefaultTaskScheduler(options(), Clock.system(), CompletionDispatcher.direct());
		created.start();
		return created;
	}

	private static SchedulerOptions options() {
		return SchedulerOptions.builder()
				.queueCapacity(64)
				.workerThreads(2)
				.defaultGroupMaxQueued(64)
				.shutdownTimeout(Duration.ofSeconds(2))
				.build();
	}
}
