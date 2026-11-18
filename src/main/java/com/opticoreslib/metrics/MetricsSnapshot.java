package com.opticoreslib.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of every metric in a registry.
 *
 * <p>Snapshots never expose the live, mutable collections behind the metrics, so a
 * consumer can safely hold on to a snapshot while the registry keeps running.
 *
 * @param namespace the namespace the snapshot was taken from
 * @param timestamp wall-clock instant the snapshot was taken
 * @param metrics   metric snapshots keyed by fully qualified metric name
 */
public record MetricsSnapshot(String namespace, Instant timestamp, Map<String, MetricSnapshot> metrics) {

	public MetricsSnapshot {
		if (namespace == null) {
			namespace = "";
		}
		if (timestamp == null) {
			timestamp = Instant.EPOCH;
		}
		metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
	}

	/** Looks up a single metric snapshot. */
	public java.util.Optional<MetricSnapshot> metric(String name) {
		return java.util.Optional.ofNullable(metrics.get(name));
	}

	/** An empty snapshot. */
	public static MetricsSnapshot empty(String namespace) {
		return new MetricsSnapshot(namespace, Instant.EPOCH, Map.of());
	}
}
