package com.opticoreslib.core.sync;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic frame counter.
 *
 * <p>Instances are independent; there is no global frame counter in the library. A consumer
 * that needs to correlate work with rendered frames owns the counter and hands the value to
 * the library.
 */
public final class FrameCounter {

	private final AtomicLong current = new AtomicLong();

	/** Returns the current frame number without advancing it. */
	public long current() {
		return current.get();
	}

	/** Advances and returns the frame number. */
	public long next() {
		return current.incrementAndGet();
	}

	/** Resets the counter to zero. */
	public void reset() {
		current.set(0L);
	}
}
