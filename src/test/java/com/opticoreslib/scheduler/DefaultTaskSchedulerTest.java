package com.opticoreslib.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.opticoreslib.core.Clock;
import com.opticoreslib.core.ServiceState;
import com.opticoreslib.task.CancellationSource;
import com.opticoreslib.task.CompletionDispatcher;
import com.opticoreslib.task.SubmissionResult;
import com.opticoreslib.task.SubmissionStatus;
import com.opticoreslib.task.Task;
import com.opticoreslib.task.TaskDescriptor;
import com.opticoreslib.task.TaskGroup;
import com.opticoreslib.task.TaskPriority;
import com.opticoreslib.task.TaskResult;
import com.opticoreslib.testing.TestSupport;

class DefaultTaskSchedulerTest {

	private DefaultTaskScheduler scheduler;

	@AfterEach
	void tearDown() {
		if (scheduler != null) {
			scheduler.close();
			scheduler = null;
		}
	}

	@Test
	void runsHigherPriorityTasksFirst() throws Exception {
		scheduler = start(options(64, 1));
		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			blockerStarted.countDown();
			release.await();
		}));
		assertTrue(TestSupport.await(blockerStarted, 2000L));

		List<String> order = new CopyOnWriteArrayList<>();
		CountDownLatch finished = new CountDownLatch(3);
		submitRecording("low", TaskPriority.LOW, order, finished);
		submitRecording("normal", TaskPriority.NORMAL, order, finished);
		submitRecording("high", TaskPriority.HIGH, order, finished);

		release.countDown();
		assertTrue(TestSupport.await(finished, 5000L));

		assertEquals(List.of("high", "normal", "low"), order);
	}

	@Test
	void keepsSubmissionOrderInsideOnePriority() throws Exception {
		scheduler = start(options(64, 1));
		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			blockerStarted.countDown();
			release.await();
		}));
		assertTrue(TestSupport.await(blockerStarted, 2000L));

		List<String> order = new CopyOnWriteArrayList<>();
		CountDownLatch finished = new CountDownLatch(3);
		submitRecording("first", TaskPriority.NORMAL, order, finished);
		submitRecording("second", TaskPriority.NORMAL, order, finished);
		submitRecording("third", TaskPriority.NORMAL, order, finished);

		release.countDown();
		assertTrue(TestSupport.await(finished, 5000L));

		assertEquals(List.of("first", "second", "third"), order);
	}

	@Test
	void refusesSubmissionsWhenTheQueueIsFull() throws Exception {
		scheduler = start(options(1, 1));
		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			blockerStarted.countDown();
			release.await();
		}));
		assertTrue(TestSupport.await(blockerStarted, 2000L));

		assertTrue(scheduler.submit(Task.noop()).isAccepted());
		SubmissionResult rejected = scheduler.submit(Task.noop());

		assertEquals(SubmissionStatus.REJECTED_CAPACITY, rejected.status());
		assertTrue(rejected.isRejected());
		assertFalse(rejected.reason().isEmpty());
		release.countDown();
	}

	@Test
	void coalescesEquivalentTasks() throws Exception {
		scheduler = start(options(64, 1));
		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			blockerStarted.countDown();
			release.await();
		}));
		assertTrue(TestSupport.await(blockerStarted, 2000L));

		AtomicInteger firstRuns = new AtomicInteger();
		AtomicInteger secondRuns = new AtomicInteger();
		SubmissionResult first = scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> firstRuns.incrementAndGet()))
				.coalescingKey("chunk-12")
				.build());
		SubmissionResult second = scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> secondRuns.incrementAndGet()))
				.coalescingKey("chunk-12")
				.build());

		assertEquals(SubmissionStatus.ACCEPTED, first.status());
		assertEquals(SubmissionStatus.COALESCED, second.status());
		assertTrue(second.isCoalesced());

		release.countDown();
		assertTrue(TestSupport.await(() -> firstRuns.get() + secondRuns.get() > 0, 5000L));
		Thread.sleep(100L);

		assertEquals(1, firstRuns.get() + secondRuns.get(), "only the newest payload must run");
	}

	@Test
	void neverRunsAnAlreadyCancelledTask() {
		scheduler = start(options(64, 2));
		CancellationSource source = CancellationSource.create();
		source.cancel();

		AtomicInteger runs = new AtomicInteger();
		SubmissionResult result = scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> runs.incrementAndGet()))
				.cancellation(source)
				.build());

		assertEquals(SubmissionStatus.CANCELLED, result.status());
		assertEquals(0, runs.get());
	}

	@Test
	void refusesSubmissionsAfterStopAccepting() {
		scheduler = start(options(64, 2));
		scheduler.stopAccepting();

		SubmissionResult result = scheduler.submit(Task.noop());

		assertEquals(SubmissionStatus.REJECTED_CLOSED, result.status());
		assertEquals(ServiceState.STOPPING, scheduler.state());
	}

	@Test
	void defersSubmissionsUnderBackpressure() {
		scheduler = new DefaultTaskScheduler(
				SchedulerOptions.builder()
						.queueCapacity(64)
						.workerThreads(2)
						.backpressure(() -> true)
						.build(),
				Clock.system(),
				CompletionDispatcher.direct());
		scheduler.start();

		SubmissionResult result = scheduler.submit(Task.noop());

		assertEquals(SubmissionStatus.DEFERRED, result.status());
		assertEquals(1, scheduler.stats().deferred());
	}

	@Test
	void enforcesThePerGroupQueueBound() throws Exception {
		scheduler = start(options(64, 1));
		scheduler.registerGroup(TaskGroup.of("chunks", 2, Integer.MAX_VALUE));

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			blockerStarted.countDown();
			release.await();
		}));
		assertTrue(TestSupport.await(blockerStarted, 2000L));

		assertTrue(scheduler.submit(grouped("chunks", "a")).isAccepted());
		assertTrue(scheduler.submit(grouped("chunks", "b")).isAccepted());
		SubmissionResult rejected = scheduler.submit(grouped("chunks", "c"));

		assertEquals(SubmissionStatus.REJECTED_CAPACITY, rejected.status());
		release.countDown();
	}

	@Test
	void reportsFailuresAsValuesInsteadOfThrowing() throws Exception {
		scheduler = start(options(64, 2));
		AtomicReference<TaskResult> result = new AtomicReference<>();
		CountDownLatch finished = new CountDownLatch(1);

		scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> {
					throw new IllegalStateException("boom");
				}))
				.onComplete((context, taskResult) -> {
					result.set(taskResult);
					finished.countDown();
				})
				.build());

		assertTrue(TestSupport.await(finished, 5000L));
		assertTrue(result.get() instanceof TaskResult.Failure);
		TaskResult.Failure failure = (TaskResult.Failure) result.get();
		assertEquals("java.lang.IllegalStateException", failure.errorType());
		assertEquals("boom", failure.message());
		assertFalse(failure.isSuccess());
	}

	@Test
	void keepsRunningAfterATaskFailed() throws Exception {
		scheduler = start(options(64, 1));
		CountDownLatch finished = new CountDownLatch(1);
		scheduler.submit(Task.of(ctx -> {
			throw new RuntimeException("boom");
		}));
		scheduler.submit(Task.of(ctx -> finished.countDown()));

		assertTrue(TestSupport.await(finished, 5000L));
		assertEquals(1, scheduler.stats().failed());
	}

	@Test
	void drainsPendingWorkOnShutdown() {
		scheduler = start(options(64, 1));
		AtomicInteger runs = new AtomicInteger();
		for (int i = 0; i < 20; i++) {
			scheduler.submit(Task.of(ctx -> runs.incrementAndGet()));
		}

		scheduler.stopAccepting();
		scheduler.drain(Duration.ofSeconds(5));

		assertEquals(20, runs.get());
		assertEquals(0, scheduler.queueDepth());
	}

	@Test
	void leavesNoWorkerThreadsBehind() throws Exception {
		int before = TestSupport.countThreads("opticoreslib-scheduler");
		DefaultTaskScheduler local = start(options(64, 3));
		assertTrue(TestSupport.await(() -> TestSupport.countThreads("opticoreslib-scheduler") > before, 2000L));

		local.close();
		scheduler = null;

		assertTrue(TestSupport.await(() -> TestSupport.countThreads("opticoreslib-scheduler") <= before, 5000L));
	}

	@Test
	void exposesGroupStatistics() throws Exception {
		scheduler = start(options(64, 1));
		scheduler.registerGroup(TaskGroup.of("chunks"));
		CountDownLatch finished = new CountDownLatch(1);
		scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> finished.countDown()))
				.group("chunks")
				.build());

		assertTrue(TestSupport.await(finished, 5000L));
		assertTrue(TestSupport.await(() -> scheduler.groupStats("chunks").isPresent(), 2000L));
		assertEquals(1L, scheduler.groupStats("chunks").orElseThrow().completed());
	}

	@Test
	void closesIdempotently() {
		scheduler = start(options(64, 1));
		scheduler.close();
		scheduler.close();
		assertEquals(ServiceState.CLOSED, scheduler.state());
		assertNotNull(scheduler.stats());
	}

	private void submitRecording(String name, TaskPriority priority, List<String> order, CountDownLatch finished) {
		scheduler.submit(TaskDescriptor
				.builder(Task.of(ctx -> {
				}))
				.priority(priority)
				.id(name)
				.onComplete((context, result) -> {
					order.add(name);
					finished.countDown();
				})
				.build());
	}

	private TaskDescriptor grouped(String group, String id) {
		return TaskDescriptor.builder(Task.noop()).group(group).id(id).build();
	}

	private static DefaultTaskScheduler start(SchedulerOptions options) {
		DefaultTaskScheduler created = new DefaultTaskScheduler(options, Clock.system(), CompletionDispatcher.direct());
		created.start();
		return created;
	}

	private static SchedulerOptions options(int capacity, int threads) {
		return SchedulerOptions.builder()
				.queueCapacity(capacity)
				.workerThreads(threads)
				.defaultGroupMaxQueued(64)
				.shutdownTimeout(Duration.ofSeconds(2))
				.build();
	}
}
