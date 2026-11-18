package com.opticoreslib.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Source of short, human readable, unique identifiers.
 *
 * <p>Identifiers are prefix + monotonically increasing counter. They are unique within a
 * JVM instance and are meant for logs, metrics labels and debugging, not for persistence.
 */
public final class Ids {

	private static final AtomicLong GLOBAL = new AtomicLong();

	private final String prefix;
	private final AtomicLong counter = new AtomicLong();

	public Ids(String prefix) {
		this.prefix = (prefix == null || prefix.isBlank()) ? "id" : prefix.trim();
	}

	/** Creates a generator with the given prefix. */
	public static Ids of(String prefix) {
		return new Ids(prefix);
	}

	/** The configured prefix. */
	public String prefix() {
		return prefix;
	}

	/** Returns the next identifier. */
	public String next() {
		return prefix + "-" + counter.incrementAndGet();
	}

	/** Returns an identifier that is unique across every generator in this JVM. */
	public String nextGlobal() {
		return prefix + "-g" + GLOBAL.incrementAndGet();
	}

	@Override
	public String toString() {
		return prefix;
	}
}
