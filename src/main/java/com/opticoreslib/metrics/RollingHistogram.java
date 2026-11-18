package com.opticoreslib.metrics;

import java.util.Arrays;

/**
 * Fixed capacity, rolling histogram.
 *
 * <p>Storage is a ring buffer of {@code capacity} doubles, so the memory footprint of a
 * histogram never grows. Once the buffer is full the oldest sample is overwritten, which
 * keeps the reported percentiles representative of recent behaviour instead of the whole
 * history.
 *
 * <p>Percentiles are computed from a copy of the window, so {@link #snapshot()} is O(capacity
 * log capacity). Call it sparingly, for example once per second.
 */
public final class RollingHistogram implements Histogram {

	/** Default number of retained samples. */
	public static final int DEFAULT_CAPACITY = 256;

	private final double[] window;
	private final double[] sorted;
	private int index;
	private int size;
	private long count;
	private double sum;
	private double min = Double.POSITIVE_INFINITY;
	private double max = Double.NEGATIVE_INFINITY;

	public RollingHistogram() {
		this(DEFAULT_CAPACITY);
	}

	public RollingHistogram(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be > 0");
		}
		this.window = new double[capacity];
		this.sorted = new double[capacity];
	}

	@Override
	public void record(double value) {
		if (Double.isNaN(value)) {
			return;
		}
		window[index] = value;
		index = (index + 1) % window.length;
		if (size < window.length) {
			size++;
		}
		count++;
		sum += value;
		min = Math.min(min, value);
		max = Math.max(max, value);
	}

	@Override
	public Percentiles snapshot() {
		if (size == 0) {
			return new Percentiles(count, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		}
		System.arraycopy(window, 0, sorted, 0, size);
		Arrays.sort(sorted, 0, size);
		double mean = count == 0 ? 0.0 : sum / count;
		return new Percentiles(
				count,
				sorted[0],
				sorted[size - 1],
				mean,
				percentile(sorted, size, 0.50),
				percentile(sorted, size, 0.95),
				percentile(sorted, size, 0.99));
	}

	@Override
	public long count() {
		return count;
	}

	@Override
	public void reset() {
		Arrays.fill(window, 0.0);
		index = 0;
		size = 0;
		count = 0;
		sum = 0.0;
		min = Double.POSITIVE_INFINITY;
		max = Double.NEGATIVE_INFINITY;
	}

	private static double percentile(double[] sorted, int size, double quantile) {
		if (size == 0) {
			return 0.0;
		}
		int position = (int) Math.ceil(quantile * size) - 1;
		if (position < 0) {
			position = 0;
		}
		if (position >= size) {
			position = size - 1;
		}
		return sorted[position];
	}
}
