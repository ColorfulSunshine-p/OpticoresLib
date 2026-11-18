package com.opticoreslib.concurrent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default {@link ManagedExecutor} implementation.
 *
 * <p>The pool is backed by a {@link ThreadPoolExecutor} whose core and maximum pool size
 * are equal, so the thread count never grows beyond the configured bound and a thread is
 * never created per task. The task queue is a fixed size array, so the number of waiting
 * tasks is bounded as well.
 */
public final class BoundedManagedExecutor extends AbstractExecutorService implements ManagedExecutor {

	private final ManagedExecutorConfig config;
	private final ThreadPoolExecutor delegate;
	private final AtomicBoolean accepting = new AtomicBoolean(true);
	private final AtomicBoolean closed = new AtomicBoolean();
	private final LongAdder completed = new LongAdder();
	private final LongAdder rejected = new LongAdder();
	private final LongAdder dropped = new LongAdder();
	private final LongAdder totalDurationNanos = new LongAdder();

	public BoundedManagedExecutor(ManagedExecutorConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null");
		}
		this.config = config;
		NamedThreadFactory factory = new NamedThreadFactory(config.name(), config.daemon(), config.threadPriority());
		this.delegate = new ThreadPoolExecutor(
				config.maxConcurrency(),
				config.maxConcurrency(),
				config.keepAlive().toMillis(),
				TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(config.queueCapacity()),
				factory,
				new StrategyHandler());
		this.delegate.allowCoreThreadTimeOut(true);
	}

	@Override
	public String name() {
		return config.name();
	}

	@Override
	public int maxConcurrency() {
		return config.maxConcurrency();
	}

	@Override
	public int queueCapacity() {
		return config.queueCapacity();
	}

	@Override
	public RejectionStrategy rejectionStrategy() {
		return config.rejectionStrategy();
	}

	@Override
	public void execute(Runnable command) {
		if (command == null) {
			throw new NullPointerException("command");
		}
		if (!accepting.get()) {
			rejected.increment();
			throw new RejectedExecutionException("Executor '" + name() + "' is not accepting work");
		}
		try {
			delegate.execute(new TimedTask(command));
		} catch (RejectedExecutionException e) {
			// The rejection handler already counted the refusal.
			throw e;
		}
	}

	@Override
	public void stopAccepting() {
		accepting.set(false);
	}

	@Override
	public boolean shutdownGracefully(Duration timeout) {
		stopAccepting();
		delegate.shutdown();
		try {
			return delegate.awaitTermination(Math.max(0L, timeout.toMillis()), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			delegate.shutdownNow();
			return false;
		}
	}

	@Override
	public void shutdown() {
		stopAccepting();
		delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		stopAccepting();
		return delegate.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return delegate.awaitTermination(timeout, unit);
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		stopAccepting();
		delegate.shutdown();
		try {
			if (!delegate.awaitTermination(defaultShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
				delegate.shutdownNow();
				delegate.awaitTermination(1, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			delegate.shutdownNow();
		}
	}

	@Override
	public WorkerStats stats() {
		return new WorkerStats(
				name(),
				delegate.getPoolSize(),
				delegate.getActiveCount(),
				delegate.getQueue().size(),
				completed.sum(),
				rejected.sum(),
				dropped.sum(),
				totalDurationNanos.sum());
	}

	@Override
	public String toString() {
		return "BoundedManagedExecutor[" + name() + " maxConcurrency=" + maxConcurrency()
				+ " queueCapacity=" + queueCapacity() + "]";
	}

	/** Wraps a task so that its duration and completion are counted. */
	private final class TimedTask implements Runnable {

		private final Runnable delegate;

		private TimedTask(Runnable delegate) {
			this.delegate = delegate;
		}

		@Override
		public void run() {
			long start = System.nanoTime();
			try {
				delegate.run();
			} finally {
				completed.increment();
				totalDurationNanos.add(System.nanoTime() - start);
			}
		}
	}

	/** Applies the configured {@link RejectionStrategy}. */
	private final class StrategyHandler implements RejectedExecutionHandler {

		@Override
		public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
			switch (config.rejectionStrategy()) {
				case ABORT -> {
					rejected.increment();
					throw new RejectedExecutionException("Executor '" + name() + "' is saturated");
				}
				case CALLER_RUNS -> {
					if (executor.isShutdown()) {
						rejected.increment();
						throw new RejectedExecutionException("Executor '" + name() + "' is shut down");
					}
					runnable.run();
				}
				case DROP -> dropped.increment();
				case DISCARD_OLDEST -> {
					if (executor.isShutdown()) {
						rejected.increment();
						throw new RejectedExecutionException("Executor '" + name() + "' is shut down");
					}
					Runnable discarded = executor.getQueue().poll();
					if (discarded != null) {
						dropped.increment();
					}
					executor.execute(runnable);
				}
			}
		}
	}
}
