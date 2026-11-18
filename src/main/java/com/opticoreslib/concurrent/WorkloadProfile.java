package com.opticoreslib.concurrent;

/**
 * Hint describing the kind of work a pool executes.
 *
 * <p>The library never creates pools on its own. A consumer decides how many lanes it
 * needs and configures each one explicitly, because free CPU capacity can never be
 * inferred from the core count alone.
 */
public enum WorkloadProfile {

	/** CPU bound work: heavy computation, little waiting. */
	CPU_BOUND,
	/** Blocking work: I/O that releases the thread while it waits. */
	IO_BOUND,
	/** Completion callbacks that must not delay other lanes. */
	COMPLETION;

	/**
	 * A conservative starting point for the pool size.
	 *
	 * <p>This is a suggestion only. Consumers are expected to override it with a value that
	 * reflects measured pressure rather than the raw core count.
	 */
	public int suggestedConcurrency() {
		int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
		return switch (this) {
			case CPU_BOUND -> Math.max(1, processors / 2);
			case IO_BOUND -> Math.max(2, processors);
			case COMPLETION -> 1;
		};
	}
}
