package com.opticoreslib.testing;

import java.time.Duration;
import java.time.Instant;

import com.opticoreslib.core.Clock;

/**
 * Deterministic {@link Clock} for tests.
 *
 * <p>Time only moves when a test moves it, which makes ordering, aging and timeout logic
 * fully reproducible.
 */
public final class FakeClock implements Clock {

	private final Instant start;
	private long nanos;
	private Instant instant;

	public FakeClock() {
		this(Instant.parse("2024-01-01T00:00:00Z"));
	}

	public FakeClock(Instant start) {
		this.start = start;
		this.instant = start;
	}

	@Override
	public synchronized long nanoTime() {
		return nanos;
	}

	@Override
	public synchronized Instant instant() {
		return instant;
	}

	/** Moves the clock forward. */
	public synchronized void advance(Duration duration) {
		if (duration == null || duration.isNegative()) {
			throw new IllegalArgumentException("duration must not be negative");
		}
		nanos += duration.toNanos();
		instant = instant.plus(duration);
	}

	/** Moves the clock forward by the given amount of milliseconds. */
	public synchronized void advanceMillis(long millis) {
		advance(Duration.ofMillis(millis));
	}

	/** The instant the clock was created with. */
	public Instant start() {
		return start;
	}
}
