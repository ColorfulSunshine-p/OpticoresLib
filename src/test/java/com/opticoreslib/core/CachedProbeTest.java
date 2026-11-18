package com.opticoreslib.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.opticoreslib.testing.FakeClock;

class CachedProbeTest {

	@Test
	void refreshesOnlyAfterTheTimeToLiveElapsed() {
		FakeClock clock = new FakeClock();
		AtomicInteger calls = new AtomicInteger();
		CachedProbe<Integer> probe = new CachedProbe<>(calls::incrementAndGet, clock, Duration.ofSeconds(10));

		assertEquals(1, probe.get());
		assertEquals(1, probe.get());
		assertEquals(1, calls.get());

		clock.advance(Duration.ofSeconds(9));
		assertEquals(1, probe.get());
		assertEquals(1, calls.get());

		clock.advance(Duration.ofSeconds(2));
		assertEquals(2, probe.get());
		assertEquals(2, calls.get());
	}

	@Test
	void exposesTheAgeOfTheCachedValue() {
		FakeClock clock = new FakeClock();
		CachedProbe<String> probe = new CachedProbe<>(() -> "value", clock, Duration.ofSeconds(30));

		assertEquals(Duration.ZERO, probe.age());
		probe.get();
		clock.advance(Duration.ofSeconds(5));
		assertEquals(Duration.ofSeconds(5), probe.age());
		assertTrue(probe.isFresh());
	}

	@Test
	void refreshForcesANewValue() {
		FakeClock clock = new FakeClock();
		AtomicInteger calls = new AtomicInteger();
		CachedProbe<Integer> probe = new CachedProbe<>(calls::incrementAndGet, clock, Duration.ofHours(1));

		probe.get();
		probe.refresh();

		assertEquals(2, calls.get());
		assertFalse(probe.age().isNegative());
	}
}
