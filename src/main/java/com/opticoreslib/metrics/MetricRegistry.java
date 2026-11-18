package com.opticoreslib.metrics;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleSupplier;

/**
 * Namespaced registry of counters, gauges, timers and histograms.
 *
 * <p>Every consumer owns its own registry, so metric names can never collide between
 * unrelated mods. Metric names are stored as {@code namespace.name}.
 *
 * <p>Storage is bounded: counters and gauges occupy a constant amount of memory, timers
 * and histograms keep a fixed size ring buffer.
 *
 * <p>Nothing in this class transmits data anywhere. Metrics stay local to the process.
 */
public final class MetricRegistry {

	private final String namespace;
	private final Clock wallClock;
	private final Map<String, Counter> counters = new ConcurrentHashMap<>();
	private final Map<String, Gauge> gauges = new ConcurrentHashMap<>();
	private final Map<String, Timer> timers = new ConcurrentHashMap<>();
	private final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

	public MetricRegistry(String namespace) {
		this(namespace, Clock.systemUTC());
	}

	public MetricRegistry(String namespace, Clock wallClock) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}
		this.namespace = namespace.trim();
		this.wallClock = wallClock == null ? Clock.systemUTC() : wallClock;
	}

	/** The namespace of this registry. */
	public String namespace() {
		return namespace;
	}

	/** Returns the counter with the given name, creating it on first use. */
	public Counter counter(String name) {
		return counters.computeIfAbsent(qualified(name), key -> new LongCounter());
	}

	/** Returns the gauge with the given name, creating it on first use. */
	public Gauge gauge(String name, DoubleSupplier supplier) {
		if (supplier == null) {
			throw new IllegalArgumentException("supplier must not be null");
		}
		return gauges.computeIfAbsent(qualified(name), key -> (Gauge) supplier::getAsDouble);
	}

	/** Returns the gauge with the given name and a constant value, creating it on first use. */
	public Gauge gauge(String name, double value) {
		return gauge(name, () -> value);
	}

	/** Returns the timer with the given name, creating it on first use. */
	public Timer timer(String name) {
		return timers.computeIfAbsent(qualified(name), key -> new NanosecondTimer());
	}

	/** Returns the histogram with the given name, creating it on first use. */
	public Histogram histogram(String name) {
		return histograms.computeIfAbsent(qualified(name), key -> new RollingHistogram());
	}

	/** Returns the histogram with the given name and capacity, creating it on first use. */
	public Histogram histogram(String name, int capacity) {
		return histograms.computeIfAbsent(qualified(name), key -> new RollingHistogram(capacity));
	}

	/** Takes an immutable snapshot of every registered metric. */
	public MetricsSnapshot snapshot() {
		Map<String, MetricSnapshot> snapshots = new ConcurrentHashMap<>();
		for (Map.Entry<String, Counter> entry : counters.entrySet()) {
			snapshots.put(entry.getKey(), new MetricSnapshot.CounterValue(entry.getKey(), entry.getValue().get()));
		}
		for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
			snapshots.put(entry.getKey(), new MetricSnapshot.GaugeValue(entry.getKey(), entry.getValue().get()));
		}
		for (Map.Entry<String, Timer> entry : timers.entrySet()) {
			Timer timer = entry.getValue();
			snapshots.put(entry.getKey(), new MetricSnapshot.TimerValue(
					entry.getKey(),
					timer.count(),
					timer.total().toNanos(),
					timer.min().toNanos(),
					timer.max().toNanos(),
					timer.percentiles()));
		}
		for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
			snapshots.put(entry.getKey(), new MetricSnapshot.HistogramValue(entry.getKey(), entry.getValue().snapshot()));
		}
		return new MetricsSnapshot(namespace, wallClock.instant(), snapshots);
	}

	/** Resets every metric in the registry. */
	public void reset() {
		counters.values().forEach(Counter::reset);
		timers.values().forEach(Timer::reset);
		histograms.values().forEach(Histogram::reset);
	}

	/** Number of registered metrics. */
	public int size() {
		return counters.size() + gauges.size() + timers.size() + histograms.size();
	}

	private String qualified(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("metric name must not be blank");
		}
		String trimmed = name.trim();
		return trimmed.startsWith(namespace + ".") ? trimmed : namespace + "." + trimmed;
	}

	/** Thread safe counter backed by a {@link LongAdder}. */
	private static final class LongCounter implements Counter {

		private final LongAdder value = new LongAdder();

		@Override
		public long increment() {
			value.increment();
			return value.sum();
		}

		@Override
		public long increment(long delta) {
			value.add(delta);
			return value.sum();
		}

		@Override
		public long get() {
			return value.sum();
		}

		@Override
		public void reset() {
			value.reset();
		}
	}

	/** Timer with a bounded latency distribution. */
	private static final class NanosecondTimer implements Timer {

		private final LongAdder count = new LongAdder();
		private final LongAdder total = new LongAdder();
		private final RollingHistogram latency = new RollingHistogram();
		private volatile long min = Long.MAX_VALUE;
		private volatile long max = Long.MIN_VALUE;

		@Override
		public void record(Duration duration) {
			if (duration == null) {
				return;
			}
			recordNanos(duration.toNanos());
		}

		@Override
		public void recordNanos(long nanos) {
			count.increment();
			total.add(nanos);
			latency.record(nanos);
			if (nanos < min) {
				min = nanos;
			}
			if (nanos > max) {
				max = nanos;
			}
		}

		@Override
		public long count() {
			return count.sum();
		}

		@Override
		public Duration total() {
			return Duration.ofNanos(total.sum());
		}

		@Override
		public Duration max() {
			long value = max;
			return value == Long.MIN_VALUE ? Duration.ZERO : Duration.ofNanos(value);
		}

		@Override
		public Duration min() {
			long value = min;
			return value == Long.MAX_VALUE ? Duration.ZERO : Duration.ofNanos(value);
		}

		@Override
		public Percentiles percentiles() {
			return latency.snapshot();
		}

		@Override
		public void reset() {
			count.reset();
			total.reset();
			latency.reset();
			min = Long.MAX_VALUE;
			max = Long.MIN_VALUE;
		}
	}
}
