package com.opticoreslib.core;

import java.time.Instant;

/**
 * Minimal time abstraction used across the library.
 *
 * <p>Two distinct notions of time are exposed on purpose:
 * <ul>
 *   <li>{@link #nanoTime()} is <strong>monotonic</strong> and must be used for every
 *       duration measurement (task durations, queue waits, frame budgets).</li>
 *   <li>{@link #instant()} is wall-clock time and must only be used to stamp
 *       snapshots and to reason about data freshness.</li>
 * </ul>
 *
 * <p>Mixing the two is a bug source, so the API keeps them separate. The production
 * implementation is {@link #system()}; tests should use a deterministic fake clock
 * supplied by the {@code testing} module.
 */
public interface Clock {

	/** Monotonic nanosecond timestamp. Only differences between two values are meaningful. */
	long nanoTime();

	/** Current wall-clock instant. Used for snapshot timestamps and freshness metadata. */
	Instant instant();

	/** Wall-clock epoch milliseconds. */
	default long epochMillis() {
		return instant().toEpochMilli();
	}

	/** The real, wall-clock implementation. */
	static Clock system() {
		return SystemClock.INSTANCE;
	}
}
