package com.opticoreslib.metrics;

/**
 * A monotonically increasing counter.
 */
public interface Counter {

	/** Adds one and returns the new value. */
	long increment();

	/** Adds the given amount and returns the new value. */
	long increment(long delta);

	/** Current value. */
	long get();

	/** Resets the counter to zero. */
	void reset();
}
