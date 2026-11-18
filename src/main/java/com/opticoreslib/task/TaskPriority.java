package com.opticoreslib.task;

/**
 * Relative importance of a task.
 *
 * <p>Priorities only express ordering <em>within one scheduler</em>. They never encode
 * gameplay semantics, and the library never assigns a priority on the consumer's behalf.
 */
public enum TaskPriority {

	/** Background work that may be delayed indefinitely. */
	LOW,
	/** Default priority. */
	NORMAL,
	/** Work the consumer considers latency sensitive. */
	HIGH,
	/** Work that must run before other queued work. */
	CRITICAL;

	/** {@code true} when this priority is strictly higher than {@code other}. */
	public boolean isHigherThan(TaskPriority other) {
		return ordinal() > other.ordinal();
	}

	/** {@code true} when this priority is at least as high as {@code other}. */
	public boolean isAtLeast(TaskPriority other) {
		return ordinal() >= other.ordinal();
	}
}
