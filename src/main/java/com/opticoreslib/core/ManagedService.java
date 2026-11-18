package com.opticoreslib.core;

import java.time.Duration;

/**
 * Explicit, deterministic lifecycle contract.
 *
 * <p>The order is always: {@link #start()} &rarr; (work) &rarr; {@link #stopAccepting()}
 * &rarr; {@link #drain(Duration)} &rarr; {@link #close()}.
 *
 * <p>Implementations must guarantee that:
 * <ul>
 *   <li>{@link #start()} is idempotent when the service is already running.</li>
 *   <li>{@link #stopAccepting()} never rejects with an exception; it only flips state so
 *       that later submissions observe a typed rejection outcome.</li>
 *   <li>{@link #drain(Duration)} never blocks longer than the supplied timeout.</li>
 *   <li>{@link #close()} is idempotent and releases every resource it owns, including
 *       worker threads.</li>
 * </ul>
 */
public interface ManagedService extends AutoCloseable {

	/** Human readable name, used in diagnostics and error messages. */
	String name();

	/** Current lifecycle state. */
	ServiceState state();

	/** Starts the service and moves it to {@link ServiceState#RUNNING}. */
	void start();

	/** Stops accepting new work. In-flight work is allowed to finish. */
	void stopAccepting();

	/**
	 * Waits for already accepted work to finish.
	 *
	 * @param timeout maximum amount of time to wait; must not be negative
	 */
	void drain(Duration timeout);

	/** Releases all resources. Must be idempotent. */
	@Override
	void close();

	/** {@code true} when the service is running and accepting work. */
	default boolean isRunning() {
		return state() == ServiceState.RUNNING;
	}

	/** Timeout used by {@link #close()} when no explicit timeout is supplied. */
	default Duration defaultDrainTimeout() {
		return Duration.ofSeconds(5);
	}
}
