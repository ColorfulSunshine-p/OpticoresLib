package com.opticoreslib.scheduler;

/**
 * External signal telling a scheduler that it should stop admitting work for a while.
 *
 * <p>The library never guesses how much pressure the host application is under. A consumer
 * that knows about frame time pressure or memory pressure wires it in here; the scheduler
 * then defers new submissions instead of queueing them.
 */
@FunctionalInterface
public interface BackpressureSignal {

	/** {@code true} when new submissions should be deferred. */
	boolean isSaturated();

	/** A signal that is never saturated. */
	static BackpressureSignal never() {
		return () -> false;
	}
}
