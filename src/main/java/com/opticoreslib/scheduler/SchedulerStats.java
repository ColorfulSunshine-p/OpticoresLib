package com.opticoreslib.scheduler;

/**
 * Immutable snapshot of a scheduler's counters.
 *
 * @param queued        tasks waiting in the scheduler
 * @param active        tasks currently executing
 * @param capacity      maximum number of queued tasks
 * @param workerThreads number of worker threads
 * @param completed     tasks that finished successfully
 * @param failed        tasks that finished with an error
 * @param cancelled     tasks that were cancelled
 * @param rejected      submissions that were refused
 * @param coalesced     submissions merged into an equivalent queued task
 * @param deferred      submissions deferred by the backpressure signal
 * @param totalWaitNanos summed queue wait of all finished tasks
 * @param maxWaitNanos  longest observed queue wait
 */
public record SchedulerStats(int queued,
		int active,
		int capacity,
		int workerThreads,
		long completed,
		long failed,
		long cancelled,
		long rejected,
		long coalesced,
		long deferred,
		long totalWaitNanos,
		long maxWaitNanos) {

	/** Mean queue wait in nanoseconds. */
	public double averageWaitNanos() {
		long finished = completed + failed;
		return finished == 0L ? 0.0 : (double) totalWaitNanos / finished;
	}
}
