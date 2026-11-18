package com.opticoreslib.task;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable metadata handed to a task when it executes.
 *
 * <p>Deliberately free of any Minecraft type: a task context never exposes a world, a
 * chunk or an entity, so the core API stays version neutral and trivially testable.
 */
public final class TaskContext {

	private final String taskId;
	private final String groupId;
	private final TaskPriority priority;
	private final Instant submittedAt;
	private final Instant startedAt;
	private final long sequence;
	private final CancellationToken cancellationToken;
	private final Map<String, Object> attributes;

	public TaskContext(String taskId,
			String groupId,
			TaskPriority priority,
			Instant submittedAt,
			Instant startedAt,
			long sequence,
			CancellationToken cancellationToken,
			Map<String, Object> attributes) {
		this.taskId = taskId;
		this.groupId = groupId;
		this.priority = priority;
		this.submittedAt = submittedAt;
		this.startedAt = startedAt;
		this.sequence = sequence;
		this.cancellationToken = cancellationToken;
		this.attributes = attributes == null || attributes.isEmpty() ? Map.of() : Map.copyOf(attributes);
	}

	/** Identifier of the task, unique within the scheduler that accepted it. */
	public String taskId() {
		return taskId;
	}

	/** Identifier of the task group the task belongs to. */
	public String groupId() {
		return groupId;
	}

	/** Priority the task was submitted with. */
	public TaskPriority priority() {
		return priority;
	}

	/** Wall-clock instant at which the scheduler accepted the task. */
	public Instant submittedAt() {
		return submittedAt;
	}

	/** Wall-clock instant at which the worker started the task. */
	public Instant startedAt() {
		return startedAt;
	}

	/** How long the task waited in the queue. */
	public Duration queuedFor() {
		if (submittedAt == null || startedAt == null) {
			return Duration.ZERO;
		}
		return Duration.between(submittedAt, startedAt);
	}

	/** Monotonic submission sequence, used for stable FIFO ordering inside a priority. */
	public long sequence() {
		return sequence;
	}

	/** Cancellation token of the task. */
	public CancellationToken cancellationToken() {
		return cancellationToken;
	}

	/** {@code true} when the task has been cancelled. */
	public boolean isCancelled() {
		return cancellationToken != null && cancellationToken.isCancelled();
	}

	/** Consumer supplied attribute, if present. */
	public <T> Optional<T> attribute(String key) {
		if (key == null || attributes.isEmpty()) {
			return Optional.empty();
		}
		Object value = attributes.get(key);
		if (value == null) {
			return Optional.empty();
		}
		@SuppressWarnings("unchecked")
		T typed = (T) value;
		return Optional.of(typed);
	}

	/** All consumer supplied attributes. Immutable. */
	public Map<String, Object> attributes() {
		return attributes;
	}

	@Override
	public String toString() {
		return "TaskContext[" + taskId + " group=" + groupId + " priority=" + priority + "]";
	}
}
