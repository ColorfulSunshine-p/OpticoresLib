package com.opticoreslib.concurrent;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * A bounded, observable worker pool with an explicit lifecycle.
 *
 * <p>Compared to a plain {@link ExecutorService} this adds:
 * <ul>
 *   <li>a guaranteed bound on both threads and queued tasks,</li>
 *   <li>an explicit {@link #stopAccepting()} step so that refusals are observable rather
 *       than accidental,</li>
 *   <li>utilisation metrics,</li>
 *   <li>a deterministic {@link #close()} that never leaves worker threads behind.</li>
 * </ul>
 *
 * <p>There is no global executor and no per-task thread anywhere in the library.
 */
public interface ManagedExecutor extends ExecutorService, AutoCloseable {

	/** Diagnostic name of the pool. */
	String name();

	/** Maximum number of worker threads. */
	int maxConcurrency();

	/** Maximum number of queued tasks. */
	int queueCapacity();

	/** Behaviour applied when the pool is saturated. */
	RejectionStrategy rejectionStrategy();

	/** Current utilisation snapshot. */
	WorkerStats stats();

	/**
	 * Stops accepting new tasks. Further submissions are refused with a
	 * {@link java.util.concurrent.RejectedExecutionException}.
	 */
	void stopAccepting();

	/**
	 * Stops accepting work, lets running tasks finish and waits for termination.
	 *
	 * @return {@code true} when the pool terminated within the timeout
	 */
	boolean shutdownGracefully(Duration timeout);

	/** Stops accepting work, drains and terminates the pool. Idempotent. */
	@Override
	void close();

	/** Timeout used by {@link #close()}. */
	default Duration defaultShutdownTimeout() {
		return Duration.ofSeconds(5);
	}
}
