package com.opticoreslib.concurrent;

import java.time.Duration;

/**
 * Immutable configuration of a {@link ManagedExecutor}.
 *
 * <p>Every bound is finite and explicit. There is no configuration that produces an
 * unbounded queue or an unbounded thread count.
 *
 * @param name             diagnostic name of the pool
 * @param maxConcurrency   maximum number of threads that may execute tasks simultaneously
 * @param queueCapacity    maximum number of tasks waiting for a thread
 * @param rejectionStrategy what happens when the pool is saturated
 * @param daemon           whether worker threads are daemon threads
 * @param threadPriority   worker thread priority, clamped to the legal range
 * @param keepAlive        idle time after which a worker may terminate
 */
public record ManagedExecutorConfig(String name,
		int maxConcurrency,
		int queueCapacity,
		RejectionStrategy rejectionStrategy,
		boolean daemon,
		int threadPriority,
		Duration keepAlive) {

	public ManagedExecutorConfig {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (maxConcurrency <= 0) {
			throw new IllegalArgumentException("maxConcurrency must be > 0");
		}
		if (queueCapacity <= 0) {
			throw new IllegalArgumentException("queueCapacity must be > 0");
		}
		if (rejectionStrategy == null) {
			rejectionStrategy = RejectionStrategy.ABORT;
		}
		if (keepAlive == null || keepAlive.isNegative()) {
			throw new IllegalArgumentException("keepAlive must not be negative");
		}
		threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, threadPriority));
	}

	/** Starts a builder for the given pool name. */
	public static Builder builder(String name) {
		return new Builder(name);
	}

	/** Builder for {@link ManagedExecutorConfig}. */
	public static final class Builder {

		private final String name;
		private int maxConcurrency = 1;
		private int queueCapacity = 256;
		private RejectionStrategy rejectionStrategy = RejectionStrategy.ABORT;
		private boolean daemon = true;
		private int threadPriority = Thread.NORM_PRIORITY;
		private Duration keepAlive = Duration.ofSeconds(30);

		private Builder(String name) {
			this.name = name;
		}

		/** Sets the maximum number of simultaneous worker threads. */
		public Builder maxConcurrency(int maxConcurrency) {
			this.maxConcurrency = maxConcurrency;
			return this;
		}

		/** Sets the maximum number of queued tasks. */
		public Builder queueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
			return this;
		}

		/** Sets the rejection behaviour. */
		public Builder rejectionStrategy(RejectionStrategy rejectionStrategy) {
			this.rejectionStrategy = rejectionStrategy;
			return this;
		}

		/** Selects whether worker threads are daemon threads. */
		public Builder daemon(boolean daemon) {
			this.daemon = daemon;
			return this;
		}

		/** Sets the worker thread priority. */
		public Builder threadPriority(int threadPriority) {
			this.threadPriority = threadPriority;
			return this;
		}

		/** Sets the idle keep-alive time. */
		public Builder keepAlive(Duration keepAlive) {
			this.keepAlive = keepAlive;
			return this;
		}

		/** Builds the configuration. */
		public ManagedExecutorConfig build() {
			return new ManagedExecutorConfig(name, maxConcurrency, queueCapacity, rejectionStrategy, daemon,
					threadPriority, keepAlive);
		}
	}
}
