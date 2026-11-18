package com.opticoreslib.task;

/**
 * A unit of background work.
 *
 * <p>A task is executed by a worker thread of a scheduler. It must be self contained: it
 * receives its inputs through the {@link TaskContext} and communicates its result through
 * the returned value or through the completion callback registered on the
 * {@link TaskDescriptor}.
 *
 * <p>Implementations must respect {@link TaskContext#isCancelled()} and must not assume
 * that they run on any particular thread. Minecraft thread affinity remains the consumer's
 * responsibility; use {@link CompletionDispatcher} to hop back to a specific thread.
 */
@FunctionalInterface
public interface Task {

	/**
	 * Executes the task.
	 *
	 * @param context immutable metadata and cancellation state for this execution
	 * @return an optional result payload, may be {@code null}
	 * @throws Exception any failure; the runtime converts it into a
	 *         {@link TaskResult.Failure} and never lets it kill a worker thread
	 */
	Object execute(TaskContext context) throws Exception;

	/** Body of a task that produces no value. */
	@FunctionalInterface
	interface Body {

		void run(TaskContext context) throws Exception;
	}

	/** Body of a task that produces a value. */
	@FunctionalInterface
	interface ValuedBody {

		Object run(TaskContext context) throws Exception;
	}

	/** Wraps a void task body into a {@link Task}. */
	static Task of(Body body) {
		return context -> {
			body.run(context);
			return null;
		};
	}

	/** Wraps a value producing task body into a {@link Task}. */
	static Task valueOf(ValuedBody body) {
		return body::run;
	}

	/** A task that does nothing. */
	static Task noop() {
		return context -> null;
	}
}
