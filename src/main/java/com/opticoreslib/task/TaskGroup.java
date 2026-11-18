package com.opticoreslib.task;

/**
 * Configuration and cancellation handle for a family of tasks.
 *
 * <p>A group only carries configuration and a cancellation scope; runtime counters live in
 * the scheduler that owns the group, so a group can be created and shared without any
 * hidden state.
 *
 * <p>{@code maxQueued} is always a positive, finite number. The library never creates an
 * unbounded group.
 */
public final class TaskGroup {

	/** Identifier used when a task does not name a group explicitly. */
	public static final String DEFAULT_GROUP = "default";

	private final String id;
	private final int maxQueued;
	private final int maxConcurrent;
	private final CancellationScope scope;

	public TaskGroup(String id, int maxQueued, int maxConcurrent) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("group id must not be blank");
		}
		if (maxQueued <= 0) {
			throw new IllegalArgumentException("maxQueued must be > 0");
		}
		if (maxConcurrent <= 0) {
			throw new IllegalArgumentException("maxConcurrent must be > 0");
		}
		this.id = id.trim();
		this.maxQueued = maxQueued;
		this.maxConcurrent = maxConcurrent;
		this.scope = CancellationScope.named(this.id);
	}

	/** A group with the default queue bound and no per-group concurrency limit. */
	public static TaskGroup of(String id) {
		return new TaskGroup(id, 256, Integer.MAX_VALUE);
	}

	/** A group with the default queue bound and an explicit concurrency limit. */
	public static TaskGroup of(String id, int maxQueued, int maxConcurrent) {
		return new TaskGroup(id, maxQueued, maxConcurrent);
	}

	/** Identifier of the group. */
	public String id() {
		return id;
	}

	/** Maximum number of tasks of this group that may be queued at the same time. */
	public int maxQueued() {
		return maxQueued;
	}

	/**
	 * Maximum number of tasks of this group that may execute at the same time.
	 *
	 * <p>{@link Integer#MAX_VALUE} means the group does not impose its own limit; the
	 * scheduler's worker count then remains the effective global bound.
	 */
	public int maxConcurrent() {
		return maxConcurrent;
	}

	/** Cancellation token of the group. */
	public CancellationToken cancellationToken() {
		return scope.token();
	}

	/** The cancellation scope backing this group. */
	public CancellationScope scope() {
		return scope;
	}

	/** Cancels every task of this group, queued or running. */
	public void cancelAll() {
		scope.cancel();
	}

	/** {@code true} when the group has been cancelled. */
	public boolean isCancelled() {
		return scope.isCancelled();
	}

	@Override
	public String toString() {
		return "TaskGroup[" + id + " maxQueued=" + maxQueued + " maxConcurrent=" + maxConcurrent + "]";
	}
}
