package com.opticoreslib.core;

/**
 * Lifecycle states shared by every {@link ManagedService} in the library.
 *
 * <p>The progression is always forward-only:
 * {@code NEW -> STARTING -> RUNNING -> STOPPING -> STOPPED -> CLOSED}, with
 * {@link #FAILED} reachable from any non-terminal state.
 */
public enum ServiceState {

	/** Created but never started. */
	NEW,
	/** {@code start()} is in progress. */
	STARTING,
	/** Accepting work. */
	RUNNING,
	/** No longer accepting work, but still finishing what it already holds. */
	STOPPING,
	/** All work has drained; resources may still be held until {@code close()}. */
	STOPPED,
	/** Startup or runtime failure. The service is not usable. */
	FAILED,
	/** Fully released. Terminal. */
	CLOSED;

	/** {@code true} once the service can no longer transition on its own. */
	public boolean isTerminal() {
		return this == STOPPED || this == CLOSED || this == FAILED;
	}

	/** {@code true} while the service still holds or may accept work. */
	public boolean isActive() {
		return this == STARTING || this == RUNNING || this == STOPPING;
	}
}
