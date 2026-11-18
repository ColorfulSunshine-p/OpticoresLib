package com.opticoreslib.metrics;

import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks how much of a per-frame budget has been consumed.
 *
 * <p>The smoothed value is an exponential moving average, which reacts to sustained
 * overload far faster than a plain mean while staying resistant to a single slow frame.
 *
 * <p>All durations are measured with a monotonic clock.
 */
public final class FrameBudget {

	/** Default smoothing factor of the exponential moving average. */
	public static final double DEFAULT_ALPHA = 0.1;

	private final double budgetNanos;
	private final double alpha;
	private final LongAdder totalNanos = new LongAdder();
	private final LongAdder frames = new LongAdder();
	private volatile double smoothedNanos;
	private volatile boolean initialised;

	public FrameBudget(double budgetMs) {
		this(budgetMs, DEFAULT_ALPHA);
	}

	public FrameBudget(double budgetMs, double alpha) {
		if (budgetMs <= 0.0) {
			throw new IllegalArgumentException("budgetMs must be > 0");
		}
		if (alpha <= 0.0 || alpha > 1.0) {
			throw new IllegalArgumentException("alpha must be within (0, 1]");
		}
		this.budgetNanos = budgetMs * 1_000_000.0;
		this.alpha = alpha;
	}

	/** Records the duration of one frame in nanoseconds. */
	public void record(long nanos) {
		if (nanos < 0) {
			return;
		}
		totalNanos.add(nanos);
		frames.increment();
		synchronized (this) {
			if (!initialised) {
				smoothedNanos = nanos;
				initialised = true;
			} else {
				smoothedNanos += alpha * (nanos - smoothedNanos);
			}
		}
	}

	/** Mean frame duration in milliseconds. */
	public double averageMs() {
		long count = frames.sum();
		return count == 0L ? 0.0 : (totalNanos.sum() / (double) count) / 1_000_000.0;
	}

	/** Smoothed frame duration in milliseconds. */
	public double smoothedMs() {
		return smoothedNanos / 1_000_000.0;
	}

	/** Smoothed frame duration as a fraction of the budget. {@code 1.0} means exactly on budget. */
	public double budgetUtilisation() {
		return budgetNanos <= 0.0 ? 0.0 : smoothedNanos / budgetNanos;
	}

	/** {@code true} when the smoothed frame duration exceeds the budget. */
	public boolean isOverBudget() {
		return smoothedNanos > budgetNanos;
	}

	/** Number of recorded frames. */
	public long frameCount() {
		return frames.sum();
	}

	/** Clears all recorded data. */
	public void reset() {
		totalNanos.reset();
		frames.reset();
		synchronized (this) {
			smoothedNanos = 0.0;
			initialised = false;
		}
	}
}
