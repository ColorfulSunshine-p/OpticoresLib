package com.opticoreslib.task;

/**
 * Handle returned when registering a cancellation listener.
 *
 * <p>Disposing the handle removes the listener, which matters for long lived consumers
 * that register and drop listeners repeatedly.
 */
@FunctionalInterface
public interface CancellationRegistration extends AutoCloseable {

	/** Removes the listener. Must be idempotent. */
	void dispose();

	@Override
	default void close() {
		dispose();
	}

	/** A registration that does nothing. */
	static CancellationRegistration none() {
		return () -> {
		};
	}
}
