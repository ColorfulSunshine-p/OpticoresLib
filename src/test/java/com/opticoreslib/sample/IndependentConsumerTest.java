package com.opticoreslib.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.opticoreslib.concurrent.BoundedManagedExecutor;
import com.opticoreslib.concurrent.ManagedExecutorConfig;
import com.opticoreslib.core.Clock;
import com.opticoreslib.metrics.MetricRegistry;
import com.opticoreslib.scheduler.DefaultTaskScheduler;
import com.opticoreslib.scheduler.SchedulerOptions;
import com.opticoreslib.task.CompletionDispatcher;
import com.opticoreslib.task.SubmissionStatus;
import com.opticoreslib.task.Task;
import com.opticoreslib.task.TaskDescriptor;
import com.opticoreslib.task.TaskGroup;
import com.opticoreslib.task.TaskResult;

/**
 * A consumer that knows nothing about OptiCores.
 *
 * <p>This mirrors the "independent sample consumer" deliverable of the implementation
 * plan: it compiles against the public API of the library only, uses no Minecraft class
 * and no OptiCores class, and therefore proves that the library is genuinely reusable.
 */
class IndependentConsumerTest {

	@Test
	void usesTheLibraryWithoutAnyOptiCoresDependency() throws Exception {
		MetricRegistry metrics = new MetricRegistry("sample");
		DefaultTaskScheduler scheduler = new DefaultTaskScheduler(
				SchedulerOptions.builder().queueCapacity(16).workerThreads(2).build(),
				Clock.system(),
				CompletionDispatcher.direct());
		BoundedManagedExecutor completionLane = new BoundedManagedExecutor(ManagedExecutorConfig
				.builder("sample-completion")
				.maxConcurrency(1)
				.queueCapacity(16)
				.build());

		try {
			scheduler.start();
			scheduler.registerGroup(TaskGroup.of("sample", 8, 2));

			CountDownLatch finished = new CountDownLatch(3);
			AtomicInteger processed = new AtomicInteger();

			for (int i = 0; i < 3; i++) {
				int index = i;
				var result = scheduler.submit(TaskDescriptor
						.builder(Task.valueOf(context -> {
							processed.incrementAndGet();
							metrics.counter("processed").increment();
							return index;
						}))
						.group("sample")
						.coalescingKey("sample-" + index)
						.onComplete((context, taskResult) -> {
							if (taskResult instanceof TaskResult.Success) {
								finished.countDown();
							}
						})
						.build());

				assertEquals(SubmissionStatus.ACCEPTED, result.status());
			}

			assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));
			assertEquals(3, processed.get());
			assertEquals(3, metrics.counter("processed").get());

			scheduler.stopAccepting();
			scheduler.drain(Duration.ofSeconds(2));
		} finally {
			completionLane.close();
			scheduler.close();
		}
	}
}
