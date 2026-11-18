package com.opticoreslib.metrics;

/**
 * A bounded distribution of samples.
 */
public interface Histogram {

	/** Records a sample. */
	void record(double value);

	/** Snapshot of the recorded samples. */
	Percentiles snapshot();

	/** Number of recorded samples. */
	long count();

	/** Clears all samples. */
	void reset();
}
