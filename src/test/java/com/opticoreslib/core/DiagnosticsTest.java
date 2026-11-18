package com.opticoreslib.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class DiagnosticsTest {

	@Test
	void reportsEachMessageAtMostTheConfiguredNumberOfTimes() {
		List<String> reported = new ArrayList<>();
		Diagnostics diagnostics = new Diagnostics((message, cause, occurrences) -> reported.add(message), 2);

		diagnostics.error("boom");
		diagnostics.error("boom");
		diagnostics.error("boom");
		diagnostics.error("boom");

		assertEquals(3, reported.size());
		assertEquals("boom", reported.get(0));
		assertEquals("boom", reported.get(1));
		assertTrue(reported.get(2).contains("suppressed"));
		assertEquals(4, diagnostics.occurrences("boom"));
	}

	@Test
	void countsMessagesIndependently() {
		AtomicInteger calls = new AtomicInteger();
		Diagnostics diagnostics = new Diagnostics((message, cause, occurrences) -> calls.incrementAndGet(), 1);

		diagnostics.error("first");
		diagnostics.error("second");

		assertEquals(2, calls.get());
		assertEquals(1, diagnostics.occurrences("first"));
		assertEquals(1, diagnostics.occurrences("second"));
	}

	@Test
	void resetClearsCounters() {
		Diagnostics diagnostics = new Diagnostics((message, cause, occurrences) -> {
		}, 1);
		diagnostics.error("boom");
		assertEquals(1, diagnostics.occurrences("boom"));
		diagnostics.reset();
		assertEquals(0, diagnostics.occurrences("boom"));
	}

	@Test
	void disabledInstanceNeverReports() {
		AtomicInteger calls = new AtomicInteger();
		Diagnostics diagnostics = new Diagnostics((message, cause, occurrences) -> calls.incrementAndGet(), 1);
		assertFalse(diagnostics.occurrences("boom") > 0);
		assertEquals(0, calls.get());
	}
}
