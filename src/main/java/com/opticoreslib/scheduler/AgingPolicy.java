package com.opticoreslib.scheduler;

import java.time.Duration;

import com.opticoreslib.task.TaskPriority;

/**
 * Optional policy that mitigates starvation by promoting tasks that have waited too long.
 *
 * <p>Aging is never applied globally and never mandatory. A consumer opts in by supplying
 * a policy, and the policy only ever influences ordering inside one scheduler.
 */
@FunctionalInterface
public interface AgingPolicy {

	/**
	 * Returns the effective priority of a task that has been waiting for the given duration.
	 *
	 * @param declared the priority the task was submitted with
	 * @param waiting  how long the task has been waiting
	 */
	TaskPriority effectivePriority(TaskPriority declared, Duration waiting);

	/** A policy that never changes the declared priority. */
	static AgingPolicy none() {
		return (declared, waiting) -> declared;
	}

	/**
	 * A policy that promotes a task by one level every {@code threshold} of waiting, up to
	 * {@code ceiling}.
	 *
	 * <p>Because the promotion depends on real waiting time, the scheduler refreshes its
	 * ordering at a bounded resolution rather than continuously.
	 */
	static AgingPolicy byThreshold(TaskPriority ceiling, Duration threshold) {
		if (ceiling == null) {
			throw new IllegalArgumentException("ceiling must not be null");
		}
		if (threshold == null || threshold.isZero() || threshold.isNegative()) {
			throw new IllegalArgumentException("threshold must be positive");
		}
		long thresholdNanos = threshold.toNanos();
		int ceilingOrdinal = ceiling.ordinal();
		return (declared, waiting) -> {
			if (waiting == null || waiting.isNegative() || waiting.isZero()) {
				return declared;
			}
			long steps = waiting.toNanos() / thresholdNanos;
			if (steps <= 0L) {
				return declared;
			}
			int boosted = declared.ordinal() + (int) Math.min(steps, ceilingOrdinal);
			return TaskPriority.values()[Math.min(boosted, ceilingOrdinal)];
		};
	}
}
