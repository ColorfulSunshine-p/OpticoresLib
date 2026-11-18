package com.opticoreslib.task;

/**
 * Mutable controller for a {@link CancellationToken}.
 *
 * <p>Cancellation is a one-way transition. {@link #cancel()} is idempotent and safe to
 * call from any thread.
 */
public interface CancellationSource {

	/** The token observed by the work this source controls. */
	CancellationToken token();

	/** {@code true} once {@link #cancel()} has been called. */
	boolean isCancelled();

	/** Requests cancellation. Idempotent. */
	void cancel();

	/** Creates a new, independent cancellation source. */
	static CancellationSource create() {
		return new MutableCancellationSource();
	}
}
