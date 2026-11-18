package com.opticoreslib.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory that produces predictably named threads.
 *
 * <p>Daemon or non-daemon is always chosen explicitly by the consumer: the library never
 * decides on its own whether it is allowed to keep the JVM alive.
 */
public final class NamedThreadFactory implements ThreadFactory {

	private final String prefix;
	private final boolean daemon;
	private final int priority;
	private final Thread.UncaughtExceptionHandler exceptionHandler;
	private final AtomicInteger counter = new AtomicInteger();

	public NamedThreadFactory(String prefix, boolean daemon, int priority) {
		this(prefix, daemon, priority, null);
	}

	public NamedThreadFactory(String prefix,
			boolean daemon,
			int priority,
			Thread.UncaughtExceptionHandler exceptionHandler) {
		if (prefix == null || prefix.isBlank()) {
			throw new IllegalArgumentException("prefix must not be blank");
		}
		this.prefix = prefix.trim();
		this.daemon = daemon;
		this.priority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
		this.exceptionHandler = exceptionHandler;
	}

	/** The configured thread name prefix. */
	public String prefix() {
		return prefix;
	}

	/** {@code true} when produced threads are daemon threads. */
	public boolean isDaemon() {
		return daemon;
	}

	@Override
	public Thread newThread(Runnable runnable) {
		Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
		thread.setDaemon(daemon);
		thread.setPriority(priority);
		if (exceptionHandler != null) {
			thread.setUncaughtExceptionHandler(exceptionHandler);
		}
		return thread;
	}
}
