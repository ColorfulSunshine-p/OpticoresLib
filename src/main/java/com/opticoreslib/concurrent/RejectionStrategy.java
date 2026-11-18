package com.opticoreslib.concurrent;

/**
 * What a worker pool does when a submission arrives while the pool is saturated.
 */
public enum RejectionStrategy {

	/** Throw a {@link java.util.concurrent.RejectedExecutionException}. */
	ABORT,
	/** Run the task on the calling thread. Never rejects, but slows the caller down. */
	CALLER_RUNS,
	/** Silently discard the task. The drop is counted and observable. */
	DROP,
	/** Discard the oldest queued task and retry the submission. */
	DISCARD_OLDEST;

	/** {@code true} when the submission is refused rather than executed. */
	public boolean isRefusing() {
		return this == ABORT;
	}
}
