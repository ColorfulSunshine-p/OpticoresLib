package com.opticoreslib.metrics;

/**
 * A value that is sampled at snapshot time rather than accumulated.
 */
@FunctionalInterface
public interface Gauge {

	/** Current value. */
	double get();

	/** A gauge that always reports the given constant. */
	static Gauge of(double value) {
		return () -> value;
	}
}
