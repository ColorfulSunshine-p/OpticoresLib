package com.opticoreslib.task;

/**
 * Immutable runtime counters of a task group.
 *
 * @param queued    tasks of this group currently waiting in the scheduler
 * @param active    tasks of this group currently executing
 * @param completed tasks of this group that finished successfully
 * @param failed    tasks of this group that finished with an error
 * @param cancelled tasks of this group that were cancelled
 * @param rejected  submissions of this group that were refused
 */
public record TaskGroupStats(int queued,
		int active,
		long completed,
		long failed,
		long cancelled,
		long rejected) {

	public TaskGroupStats {
		if (queued < 0 || active < 0) {
			throw new IllegalArgumentException("counters must not be negative");
		}
	}
}
