package com.opticoreslib.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/**
 * Deterministic {@link Executor} for tests.
 *
 * <p>Submitted work is queued and only runs when the test asks for it, which makes
 * callback ordering assertions exact.
 */
public final class ManualExecutor implements Executor {

	private final Deque<Runnable> pending = new ArrayDeque<>();

	@Override
	public synchronized void execute(Runnable command) {
		pending.addLast(command);
	}

	/** Runs the next queued task, if any. Returns {@code true} when a task was executed. */
	public synchronized boolean runNext() {
		Runnable task = pending.pollFirst();
		if (task == null) {
			return false;
		}
		task.run();
		return true;
	}

	/** Runs every queued task, including tasks queued while running. Returns the count. */
	public synchronized int runAll() {
		int executed = 0;
		while (runNext()) {
			executed++;
		}
		return executed;
	}

	/** Number of queued tasks. */
	public synchronized int pending() {
		return pending.size();
	}

	/** Discards every queued task. */
	public synchronized void clear() {
		pending.clear();
	}
}
