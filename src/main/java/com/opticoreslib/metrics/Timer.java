package com.opticoreslib.metrics;

import java.time.Duration;

/**
 * Accumulates duration samples.
 *
 * <p>Durations are always measured with a monotonic clock. Wall-clock timestamps must not
 * be used for durations.
 */
public interface Timer {

	/** Records a duration. */
	void record(Duration duration);

	/** Records a duration expressed in nanoseconds. */
	void recordNanos(long nanos);

	/** Number of recorded samples. */
	long count();

	/** Sum of all recorded durations. */
	Duration total();

	/** Longest recorded duration, or {@link Duration#ZERO}. */
	Duration max();

	/** Shortest recorded duration, or {@link Duration#ZERO}. */
	Duration min();

	/** Percentile summary of the recorded durations. */
	Percentiles percentiles();

	/** Clears all samples. */
	void reset();
}
