package com.opticoreslib.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MetricRegistryTest {

	@Test
	void namespacesMetricNames() {
		MetricRegistry registry = new MetricRegistry("opticores");
		registry.counter("chunks").increment();

		assertTrue(registry.snapshot().metrics().containsKey("opticores.chunks"));
	}

	@Test
	void doesNotDoublePrefixNames() {
		MetricRegistry registry = new MetricRegistry("opticores");
		registry.counter("opticores.chunks").increment();

		assertTrue(registry.snapshot().metrics().containsKey("opticores.chunks"));
		assertEquals(1, registry.snapshot().metrics().size());
	}

	@Test
	void countersAccumulate() {
		MetricRegistry registry = new MetricRegistry("ns");
		Counter counter = registry.counter("tasks");

		assertEquals(1, counter.increment());
		assertEquals(6, counter.increment(5));
		assertEquals(6, counter.get());

		counter.reset();
		assertEquals(0, counter.get());
	}

	@Test
	void timersAccumulateDurations() {
		MetricRegistry registry = new MetricRegistry("ns");
		Timer timer = registry.timer("latency");

		timer.record(Duration.ofMillis(10));
		timer.recordNanos(30_000_000L);

		assertEquals(2, timer.count());
		assertEquals(Duration.ofMillis(40), timer.total());
		assertEquals(Duration.ofMillis(30), timer.max());
		assertEquals(Duration.ofMillis(10), timer.min());
		assertNotNull(timer.percentiles());
	}

	@Test
	void snapshotsAreImmutable() {
		MetricRegistry registry = new MetricRegistry("ns");
		registry.counter("tasks").increment();

		MetricsSnapshot snapshot = registry.snapshot();
		assertThrows(UnsupportedOperationException.class, () -> snapshot.metrics().clear());

		registry.counter("tasks").increment();
		MetricSnapshot.CounterValue value = (MetricSnapshot.CounterValue) snapshot.metric("ns.tasks").orElseThrow();
		assertEquals(1L, value.value());
	}

	@Test
	void gaugesAreSampledAtSnapshotTime() {
		MetricRegistry registry = new MetricRegistry("ns");
		AtomicIntegerHolder holder = new AtomicIntegerHolder();
		registry.gauge("queue", () -> holder.value);

		holder.value = 3;
		MetricsSnapshot snapshot = registry.snapshot();

		MetricSnapshot.GaugeValue value = (MetricSnapshot.GaugeValue) snapshot.metric("ns.queue").orElseThrow();
		assertEquals(3.0, value.value());
	}

	@Test
	void histogramsKeepABoundedWindow() {
		RollingHistogram histogram = new RollingHistogram(4);
		for (int i = 1; i <= 10; i++) {
			histogram.record(i);
		}

		Percentiles percentiles = histogram.snapshot();
		assertEquals(10, percentiles.count());
		assertTrue(percentiles.p50() >= 7.0, "only the last four samples are retained");
		assertTrue(percentiles.max() <= 10.0);
	}

	@Test
	void frameBudgetTracksUtilisation() {
		FrameBudget budget = new FrameBudget(16.0, 1.0);

		budget.record(8_000_000L);
		assertEquals(8.0, budget.smoothedMs(), 0.0001);
		assertEquals(0.5, budget.budgetUtilisation(), 0.0001);
		assertTrue(!budget.isOverBudget());

		budget.record(40_000_000L);
		assertEquals(40.0, budget.smoothedMs(), 0.0001);
		assertTrue(budget.isOverBudget());
		assertEquals(2, budget.frameCount());
	}

	@Test
	void registryRejectsBlankNamespaces() {
		assertThrows(IllegalArgumentException.class, () -> new MetricRegistry(" "));
	}

	private static final class AtomicIntegerHolder {

		private volatile int value;

		private int value() {
			return value;
		}
	}
}
