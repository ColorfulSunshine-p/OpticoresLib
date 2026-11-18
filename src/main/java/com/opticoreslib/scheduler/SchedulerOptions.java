package com.opticoreslib.scheduler;

import java.time.Duration;

/**
 * Immutable configuration of a {@link TaskScheduler}.
 *
 * <p>Every bound is finite. The scheduler never creates an unbounded queue and never
 * creates a thread per task.
 *
 * @param queueCapacity        maximum number of tasks waiting in the scheduler
 * @param workerThreads        number of worker threads that execute tasks
 * @param defaultGroupMaxQueued queue bound applied to implicitly created task groups
 * @param daemonWorkers        whether worker threads are daemon threads
 * @param shutdownTimeout      timeout used while draining and closing
 * @param agingResolution      how often the ordering is refreshed when aging is enabled
 * @param agingPolicy          optional starvation mitigation, {@code null} disables aging
 * @param backpressure         external signal that defers new submissions
 */
public record SchedulerOptions(int queueCapacity,
		int workerThreads,
		int defaultGroupMaxQueued,
		boolean daemonWorkers,
		Duration shutdownTimeout,
		Duration agingResolution,
		AgingPolicy agingPolicy,
		BackpressureSignal backpressure) {

	public SchedulerOptions {
		if (queueCapacity <= 0) {
			throw new IllegalArgumentException("queueCapacity must be > 0");
		}
		if (workerThreads <= 0) {
			throw new IllegalArgumentException("workerThreads must be > 0");
		}
		if (defaultGroupMaxQueued <= 0) {
			throw new IllegalArgumentException("defaultGroupMaxQueued must be > 0");
		}
		if (shutdownTimeout == null || shutdownTimeout.isNegative()) {
			throw new IllegalArgumentException("shutdownTimeout must not be negative");
		}
		if (agingResolution == null || agingResolution.isNegative()) {
			throw new IllegalArgumentException("agingResolution must not be negative");
		}
		if (backpressure == null) {
			backpressure = BackpressureSignal.never();
		}
	}

	/** Default options: bounded queue, one worker per half core, daemon workers. */
	public static SchedulerOptions defaults() {
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
		return new SchedulerOptions(1024, threads, 256, true, Duration.ofSeconds(10), Duration.ofMillis(100), null,
				BackpressureSignal.never());
	}

	/** Starts a builder. */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder for {@link SchedulerOptions}. */
	public static final class Builder {

		private int queueCapacity = 1024;
		private int workerThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
		private int defaultGroupMaxQueued = 256;
		private boolean daemonWorkers = true;
		private Duration shutdownTimeout = Duration.ofSeconds(10);
		private Duration agingResolution = Duration.ofMillis(100);
		private AgingPolicy agingPolicy;
		private BackpressureSignal backpressure = BackpressureSignal.never();

		private Builder() {
		}

		/** Sets the maximum number of queued tasks. */
		public Builder queueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
			return this;
		}

		/** Sets the number of worker threads. */
		public Builder workerThreads(int workerThreads) {
			this.workerThreads = workerThreads;
			return this;
		}

		/** Sets the queue bound used for implicitly created groups. */
		public Builder defaultGroupMaxQueued(int defaultGroupMaxQueued) {
			this.defaultGroupMaxQueued = defaultGroupMaxQueued;
			return this;
		}

		/** Selects whether worker threads are daemon threads. */
		public Builder daemonWorkers(boolean daemonWorkers) {
			this.daemonWorkers = daemonWorkers;
			return this;
		}

		/** Sets the shutdown timeout. */
		public Builder shutdownTimeout(Duration shutdownTimeout) {
			this.shutdownTimeout = shutdownTimeout;
			return this;
		}

		/** Sets how often the ordering is refreshed when aging is enabled. */
		public Builder agingResolution(Duration agingResolution) {
			this.agingResolution = agingResolution;
			return this;
		}

		/** Enables starvation mitigation. */
		public Builder agingPolicy(AgingPolicy agingPolicy) {
			this.agingPolicy = agingPolicy;
			return this;
		}

		/** Sets the external backpressure signal. */
		public Builder backpressure(BackpressureSignal backpressure) {
			this.backpressure = backpressure;
			return this;
		}

		/** Builds the options. */
		public SchedulerOptions build() {
			return new SchedulerOptions(queueCapacity, workerThreads, defaultGroupMaxQueued, daemonWorkers,
					shutdownTimeout, agingResolution, agingPolicy, backpressure);
		}
	}
}
