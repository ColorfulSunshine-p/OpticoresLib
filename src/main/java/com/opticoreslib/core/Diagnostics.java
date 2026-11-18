package com.opticoreslib.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limited diagnostic reporting.
 *
 * <p>Library code must never spam the log. {@link Diagnostics} counts identical messages
 * and reports each distinct message at most {@code limit} times, then emits a single
 * "suppressed" notice.
 *
 * <p>The sink is deliberately a plain interface so that the version neutral core does not
 * depend on any logging framework. Platform modules provide the SLF4J backed sink.
 */
public final class Diagnostics {

	/** Destination for diagnostic events. */
	@FunctionalInterface
	public interface Sink {

		/**
		 * @param message     the diagnostic message
		 * @param cause       the failure, or {@code null}
		 * @param occurrences how many times this exact message has been seen
		 */
		void onError(String message, Throwable cause, int occurrences);
	}

	private final Sink sink;
	private final int limit;
	private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

	public Diagnostics(Sink sink, int limit) {
		if (sink == null) {
			throw new IllegalArgumentException("sink must not be null");
		}
		if (limit <= 0) {
			throw new IllegalArgumentException("limit must be > 0");
		}
		this.sink = sink;
		this.limit = limit;
	}

	/** A diagnostics instance that drops everything. Useful in tests and on servers. */
	public static Diagnostics disabled() {
		return new Diagnostics((message, cause, occurrences) -> {
		}, 1);
	}

	/** Reports a failure. */
	public void error(String message, Throwable cause) {
		if (message == null) {
			message = "unknown error";
		}
		AtomicInteger counter = counts.computeIfAbsent(message, key -> new AtomicInteger());
		int occurrences = counter.incrementAndGet();
		if (occurrences <= limit) {
			sink.onError(message, cause, occurrences);
		} else if (occurrences == limit + 1) {
			sink.onError(message + " (further occurrences suppressed)", null, occurrences);
		}
	}

	/** Reports a failure without a cause. */
	public void error(String message) {
		error(message, null);
	}

	/** How many times the given message has been reported. */
	public int occurrences(String message) {
		AtomicInteger counter = counts.get(message);
		return counter == null ? 0 : counter.get();
	}

	/** Clears all counters. */
	public void reset() {
		counts.clear();
	}
}
