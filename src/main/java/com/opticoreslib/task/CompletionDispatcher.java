package com.opticoreslib.task;

import java.util.concurrent.Executor;

/**
 * Decides where completion callbacks run.
 *
 * <p>Thread behaviour is part of the public contract:
 * <ul>
 *   <li>{@link #direct()} runs the callback inline on the thread that completed the task
 *       (a worker thread). Fastest, but not suitable for touching Minecraft state.</li>
 *   <li>{@link #of(Executor)} runs the callback on the supplied executor. Use this to hop
 *       back onto the main thread.</li>
 * </ul>
 *
 * <p>The library never hides the choice from the consumer: it is always explicit.
 */
@FunctionalInterface
public interface CompletionDispatcher {

	/** Runs the given action. Implementations must not let it throw. */
	void dispatch(Runnable action);

	/** Runs the callback inline on the completing thread. */
	static CompletionDispatcher direct() {
		return Runnable::run;
	}

	/** Runs the callback on the given executor. */
	static CompletionDispatcher of(Executor executor) {
		if (executor == null) {
			throw new IllegalArgumentException("executor must not be null");
		}
		return action -> executor.execute(action);
	}

	/** Discards the callback. Useful when a consumer only cares about side effects. */
	static CompletionDispatcher none() {
		return action -> {
		};
	}
}
