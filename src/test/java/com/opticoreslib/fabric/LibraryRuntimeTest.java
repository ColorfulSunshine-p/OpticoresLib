package com.opticoreslib.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opticoreslib.core.ServiceState;
import com.opticoreslib.metrics.MetricRegistry;
import com.opticoreslib.scheduler.TaskScheduler;
import com.opticoreslib.task.SubmissionStatus;

/**
 * Tests for the runtime lifecycle.
 *
 * <p>{@link LibraryRuntime} is free of any Fabric type, so it can be exercised without
 * launching Minecraft.
 */
class LibraryRuntimeTest {

	@Test
	void closeIsIdempotent() {
		LibraryRuntime runtime = LibraryRuntime.create();

		runtime.close();
		runtime.close();

		assertEquals(ServiceState.CLOSED, runtime.state());
	}

	@Test
	void theSchedulerIsCreatedLazilyAndOnlyOnce() {
		LibraryRuntime runtime = LibraryRuntime.create();
		try {
			assertTrue(runtime.services().isEmpty(), "no service may exist before the scheduler is requested");

			TaskScheduler scheduler = runtime.scheduler();
			assertSame(scheduler, runtime.scheduler());
			assertEquals(1, runtime.services().size());
			assertTrue(scheduler.state() == ServiceState.RUNNING);
		} finally {
			runtime.close();
		}
	}

	@Test
	void theSchedulerCannotBeCreatedAfterClose() {
		LibraryRuntime runtime = LibraryRuntime.create();
		runtime.close();

		assertThrows(IllegalStateException.class, runtime::scheduler);
	}

	@Test
	void closeStopsTheScheduler() {
		LibraryRuntime runtime = LibraryRuntime.create();
		TaskScheduler scheduler = runtime.scheduler();

		runtime.close();

		assertEquals(ServiceState.CLOSED, scheduler.state());
		assertEquals(SubmissionStatus.REJECTED_CLOSED, scheduler.submit(ctx -> null).status());
	}

	@Test
	void servicesAreReachableByType() {
		LibraryRuntime runtime = LibraryRuntime.create();
		try {
			MetricRegistry metrics = runtime.metrics();

			assertSame(metrics, runtime.service(MetricRegistry.class).orElseThrow());
			assertFalse(runtime.service(TaskScheduler.class).isPresent(),
					"the scheduler is only visible once it has been requested");

			runtime.scheduler();

			assertTrue(runtime.service(TaskScheduler.class).isPresent());
			assertFalse(runtime.service(Runnable.class).isPresent());
			assertTrue(runtime.service(null).equals(Optional.empty()));
		} finally {
			runtime.close();
		}
	}

	@Test
	void theGraphicsProbeIsOptional() {
		LibraryRuntime runtime = LibraryRuntime.create();
		try {
			assertTrue(runtime.graphicsProbe().isEmpty());

			runtime.installGraphicsProbe(() -> new com.opticoreslib.resources.GraphicsDeviceSnapshot(
					java.time.Instant.EPOCH,
					"",
					"",
					"",
					com.opticoreslib.resources.GraphicsApi.UNKNOWN,
					com.opticoreslib.resources.Measurement.unknown("test"),
					com.opticoreslib.resources.Measurement.unknown("test"),
					com.opticoreslib.resources.Measurement.unknown("test"),
					com.opticoreslib.resources.Measurement.unknown("test"),
					java.util.Set.of()));

			assertTrue(runtime.graphicsProbe().isPresent());
		} finally {
			runtime.close();
		}
	}

	@Test
	void shutdownActuallyReleasesTheRuntime() {
		LibraryRuntime runtime = LibraryRuntime.create();
		assertTrue(OpticoresLib.installRuntime(runtime));
		assertTrue(OpticoresLib.runtime().isPresent());

		OpticoresLib.shutdown("test");

		assertEquals(ServiceState.CLOSED, runtime.state());
		assertTrue(OpticoresLib.runtime().isEmpty(), "the runtime must be removed from the holder");
	}

	@Test
	void shutdownIsIdempotentAndSafeWithoutARuntime() {
		OpticoresLib.shutdown("no runtime installed");

		LibraryRuntime runtime = LibraryRuntime.create();
		OpticoresLib.installRuntime(runtime);
		OpticoresLib.shutdown("first");
		OpticoresLib.shutdown("second");

		assertEquals(ServiceState.CLOSED, runtime.state());
	}

	@Test
	void installingTwiceKeepsTheFirstRuntime() {
		LibraryRuntime first = LibraryRuntime.create();
		LibraryRuntime second = LibraryRuntime.create();
		try {
			assertTrue(OpticoresLib.installRuntime(first));
			assertFalse(OpticoresLib.installRuntime(second));

			assertSame(first, OpticoresLib.runtime().orElseThrow());
		} finally {
			OpticoresLib.shutdown("test");
			second.close();
		}
	}
}
