package com.opticoreslib.task;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.opticoreslib.core.Ids;

/**
 * Immutable description of a task that a consumer wants the scheduler to run.
 *
 * <p>A descriptor carries metadata only; it never carries a live object graph such as a
 * Minecraft world or a chunk. Everything the task needs travels through the descriptor or
 * through the task closure.
 *
 * <p>Instances are created with {@link #builder()}.
 */
public final class TaskDescriptor {

	private static final Ids IDS = Ids.of("task");

	private final String id;
	private final String groupId;
	private final TaskPriority priority;
	private final Task task;
	private final String coalescingKey;
	private final CancellationToken cancellationToken;
	private final BiConsumer<TaskContext, TaskResult> completion;
	private final Map<String, Object> attributes;

	private TaskDescriptor(Builder builder) {
		this.id = builder.id;
		this.groupId = builder.groupId;
		this.priority = builder.priority;
		this.task = builder.task;
		this.coalescingKey = builder.coalescingKey;
		this.cancellationToken = builder.cancellationToken;
		this.completion = builder.completion;
		this.attributes = builder.attributes.isEmpty() ? Map.of() : Map.copyOf(builder.attributes);
	}

	/** Starts a new builder. */
	public static Builder builder() {
		return new Builder();
	}

	/** Starts a new builder for the given task. */
	public static Builder builder(Task task) {
		return new Builder().task(task);
	}

	/** Identifier of the task. */
	public String id() {
		return id;
	}

	/** Identifier of the group the task belongs to. */
	public String groupId() {
		return groupId;
	}

	/** Priority of the task. */
	public TaskPriority priority() {
		return priority;
	}

	/** The work itself. */
	public Task task() {
		return task;
	}

	/**
	 * Key used to coalesce equivalent tasks.
	 *
	 * <p>When two queued tasks share a coalescing key, only the newest payload survives.
	 * {@code null} disables coalescing for the task.
	 */
	public String coalescingKey() {
		return coalescingKey;
	}

	/** Cancellation token observed by the task. */
	public CancellationToken cancellationToken() {
		return cancellationToken;
	}

	/** Completion callback, or {@code null}. */
	public BiConsumer<TaskContext, TaskResult> completion() {
		return completion;
	}

	/** Consumer supplied attributes. Immutable. */
	public Map<String, Object> attributes() {
		return attributes;
	}

	/** Returns a copy of this descriptor with the given task, keeping all other metadata. */
	public TaskDescriptor withTask(Task newTask) {
		Builder builder = new Builder()
				.id(id)
				.group(groupId)
				.priority(priority)
				.task(newTask)
				.cancellationToken(cancellationToken)
				.onComplete(completion);
		if (coalescingKey != null) {
			builder.coalescingKey(coalescingKey);
		}
		builder.attributes.putAll(attributes);
		return builder.build();
	}

	@Override
	public String toString() {
		return "TaskDescriptor[" + id + " group=" + groupId + " priority=" + priority + "]";
	}

	/** Builder for {@link TaskDescriptor}. */
	public static final class Builder {

		private String id = IDS.next();
		private String groupId = TaskGroup.DEFAULT_GROUP;
		private TaskPriority priority = TaskPriority.NORMAL;
		private Task task;
		private String coalescingKey;
		private CancellationToken cancellationToken = CancellationToken.none();
		private BiConsumer<TaskContext, TaskResult> completion;
		private final Map<String, Object> attributes = new LinkedHashMap<>();

		private Builder() {
		}

		/** Sets the task identifier. A generated identifier is used by default. */
		public Builder id(String id) {
			this.id = (id == null || id.isBlank()) ? IDS.next() : id.trim();
			return this;
		}

		/** Sets the group the task belongs to. */
		public Builder group(String groupId) {
			this.groupId = (groupId == null || groupId.isBlank()) ? TaskGroup.DEFAULT_GROUP : groupId.trim();
			return this;
		}

		/** Sets the priority. */
		public Builder priority(TaskPriority priority) {
			this.priority = priority == null ? TaskPriority.NORMAL : priority;
			return this;
		}

		/** Sets the work to run. Required. */
		public Builder task(Task task) {
			this.task = task;
			return this;
		}

		/**
		 * Enables coalescing for this task.
		 *
		 * @param coalescingKey key shared by equivalent tasks, or {@code null} to disable
		 */
		public Builder coalescingKey(String coalescingKey) {
			this.coalescingKey = (coalescingKey == null || coalescingKey.isBlank()) ? null : coalescingKey;
			return this;
		}

		/** Sets the cancellation token. */
		public Builder cancellationToken(CancellationToken cancellationToken) {
			this.cancellationToken = cancellationToken == null ? CancellationToken.none() : cancellationToken;
			return this;
		}

		/** Sets the cancellation token from a source. */
		public Builder cancellation(CancellationSource source) {
			this.cancellationToken = source == null ? CancellationToken.none() : source.token();
			return this;
		}

		/** Registers a completion callback. It runs through the scheduler's dispatcher. */
		public Builder onComplete(BiConsumer<TaskContext, TaskResult> completion) {
			this.completion = completion;
			return this;
		}

		/** Adds a consumer supplied attribute. */
		public Builder attribute(String key, Object value) {
			if (key != null && value != null) {
				this.attributes.put(key, value);
			}
			return this;
		}

		/** Builds the descriptor. */
		public TaskDescriptor build() {
			if (task == null) {
				throw new IllegalStateException("a task is required");
			}
			return new TaskDescriptor(this);
		}
	}
}
