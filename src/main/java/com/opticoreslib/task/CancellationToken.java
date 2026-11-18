package com.opticoreslib.task;

/**
 * Read-only view on a cancellation signal.
 *
 * <p>A token never changes identity and never transitions back from cancelled to not
 * cancelled.
 */
public interface CancellationToken {

	/** {@code true} once cancellation has been requested. */
	boolean isCancelled();

	/**
	 * Registers a listener that runs when the token is cancelled.
	 *
	 * <p>If the token is already cancelled the listener runs immediately on the calling
	 * thread and a no-op registration is returned.
	 */
	CancellationRegistration onCancel(Runnable action);

	/** A token that is never cancelled. */
	static CancellationToken none() {
		return CancellationTokens.NONE;
	}
}
