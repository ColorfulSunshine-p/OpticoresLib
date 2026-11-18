package com.opticoreslib.metrics;

/**
 * Immutable snapshot of a single metric.
 */
public sealed interface MetricSnapshot permits MetricSnapshot.CounterValue, MetricSnapshot.GaugeValue,
		MetricSnapshot.TimerValue, MetricSnapshot.HistogramValue {

	/** Name of the metric, including its namespace. */
	String name();

	/** Snapshot of a counter. */
	record CounterValue(String name, long value) implements MetricSnapshot {
	}

	/** Snapshot of a gauge. */
	record GaugeValue(String name, double value) implements MetricSnapshot {
	}

	/** Snapshot of a timer, including the latency distribution. */
	record TimerValue(String name, long count, long totalNanos, long minNanos, long maxNanos, Percentiles latency)
			implements MetricSnapshot {
	}

	/** Snapshot of a histogram. */
	record HistogramValue(String name, Percentiles percentiles) implements MetricSnapshot {
	}
}
