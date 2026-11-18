package com.opticoreslib.metrics;

/**
 * Immutable percentile summary of a sample window.
 *
 * @param count number of samples in the window
 * @param min   smallest sample
 * @param max   largest sample
 * @param mean  arithmetic mean
 * @param p50   median
 * @param p95   95th percentile
 * @param p99   99th percentile
 */
public record Percentiles(long count, double min, double max, double mean, double p50, double p95, double p99) {

	/** An empty summary. */
	public static Percentiles empty() {
		return new Percentiles(0L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
	}
}
