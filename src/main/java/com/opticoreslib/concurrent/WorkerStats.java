package com.opticoreslib.concurrent;

/**
 * Immutable snapshot of a worker pool's utilisation.
 *
 * @param name             pool name
 * @param poolSize         number of live worker threads
 * @param activeCount      number of workers currently executing a task
 * @param queuedCount      number of tasks waiting for a worker
 * @param completed        number of tasks that finished normally
 * @param rejected         number of submissions that were refused
 * @param dropped          number of tasks that were discarded
 * @param totalDurationNanos summed execution time of all completed tasks
 */
public record WorkerStats(String name,
		int poolSize,
		int activeCount,
		int queuedCount,
		long completed,
		long rejected,
		long dropped,
		long totalDurationNanos) {

	/** Fraction of workers that are currently busy, between {@code 0} and {@code 1}. */
	public double utilisation() {
		return poolSize == 0 ? 0.0 : (double) activeCount / poolSize;
	}

	/** Mean task duration in nanoseconds, or {@code 0} when nothing completed yet. */
	public double averageDurationNanos() {
		return completed == 0 ? 0.0 : (double) totalDurationNanos / completed;
	}
}
