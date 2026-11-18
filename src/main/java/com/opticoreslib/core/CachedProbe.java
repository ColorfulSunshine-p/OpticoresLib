package com.opticoreslib.core;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caches the result of an expensive supplier for a bounded amount of time.
 *
 * <p>Expensive probes (graphics capability queries, operating system statistics) must not
 * run every frame. This helper bounds how often they run and always exposes the age of the
 * cached value so consumers can reason about staleness instead of trusting a stale number
 * as if it were current.
 *
 * @param <T> cached value type
 */
public final class CachedProbe<T> {

	private final Supplier<T> supplier;
	private final Clock clock;
	private final Duration ttl;
	private volatile Entry<T> entry;

	private record Entry<T>(T value, long timestampNanos) {
	}

	public CachedProbe(Supplier<T> supplier, Clock clock, Duration ttl) {
		if (supplier == null || clock == null) {
			throw new IllegalArgumentException("supplier and clock must not be null");
		}
		if (ttl == null || ttl.isNegative()) {
			throw new IllegalArgumentException("ttl must not be negative");
		}
		this.supplier = supplier;
		this.clock = clock;
		this.ttl = ttl;
	}

	/** Returns the cached value, refreshing it when it is older than the TTL. */
	public T get() {
		Entry<T> current = entry;
		long now = clock.nanoTime();
		if (current != null && now - current.timestampNanos() < ttl.toNanos()) {
			return current.value();
		}
		return refresh();
	}

	/** Forces a refresh and returns the new value. */
	public T refresh() {
		T value = supplier.get();
		entry = new Entry<>(value, clock.nanoTime());
		return value;
	}

	/** Age of the cached value, or {@link Duration#ZERO} when nothing has been cached yet. */
	public Duration age() {
		Entry<T> current = entry;
		if (current == null) {
			return Duration.ZERO;
		}
		return Duration.ofNanos(Math.max(0L, clock.nanoTime() - current.timestampNanos()));
	}

	/** {@code true} when a value has been cached and is still within the TTL. */
	public boolean isFresh() {
		Entry<T> current = entry;
		return current != null && clock.nanoTime() - current.timestampNanos() < ttl.toNanos();
	}
}
