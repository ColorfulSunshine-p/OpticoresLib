package com.opticoreslib.resources;

import java.time.Instant;

/**
 * Immutable snapshot of CPU and memory resources.
 *
 * <p>Every field carries its own confidence and source so that a consumer can reason about
 * how much to trust it. Fields the operating system does not expose are
 * {@link Measurement#unknown(String)}, never zero.
 *
 * @param timestamp           wall-clock instant the snapshot was taken
 * @param logicalProcessors   number of logical processors reported by the operating system
 * @param availableProcessors processors currently considered available
 * @param systemMemoryTotalBytes total physical memory, when known
 * @param systemMemoryFreeBytes  free physical memory, when known
 * @param heapTotalBytes      total heap currently reserved by the JVM
 * @param heapUsedBytes       heap currently in use by the JVM
 * @param heapMaxBytes        maximum heap the JVM may grow to
 * @param systemLoadAverage   1 minute load average, when the platform exposes one
 */
public record ResourceSnapshot(Instant timestamp,
		Measurement<Integer> logicalProcessors,
		Measurement<Integer> availableProcessors,
		Measurement<Long> systemMemoryTotalBytes,
		Measurement<Long> systemMemoryFreeBytes,
		Measurement<Long> heapTotalBytes,
		Measurement<Long> heapUsedBytes,
		Measurement<Long> heapMaxBytes,
		Measurement<Double> systemLoadAverage) {

	public ResourceSnapshot {
		if (timestamp == null) {
			timestamp = Instant.EPOCH;
		}
		if (logicalProcessors == null) {
			logicalProcessors = Measurement.unknown("resource");
		}
		if (availableProcessors == null) {
			availableProcessors = Measurement.unknown("resource");
		}
		if (systemMemoryTotalBytes == null) {
			systemMemoryTotalBytes = Measurement.unknown("resource");
		}
		if (systemMemoryFreeBytes == null) {
			systemMemoryFreeBytes = Measurement.unknown("resource");
		}
		if (heapTotalBytes == null) {
			heapTotalBytes = Measurement.unknown("resource");
		}
		if (heapUsedBytes == null) {
			heapUsedBytes = Measurement.unknown("resource");
		}
		if (heapMaxBytes == null) {
			heapMaxBytes = Measurement.unknown("resource");
		}
		if (systemLoadAverage == null) {
			systemLoadAverage = Measurement.unknown("resource");
		}
	}
}
