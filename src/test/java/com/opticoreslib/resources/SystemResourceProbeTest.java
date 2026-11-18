package com.opticoreslib.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemResourceProbeTest {

	@Test
	void reportsRealCpuAndHeapNumbers() {
		ResourceSnapshot snapshot = new SystemResourceProbe().probe();

		assertNotNull(snapshot.timestamp());
		assertTrue(snapshot.logicalProcessors().isKnown());
		assertTrue(snapshot.logicalProcessors().value() > 0);
		assertTrue(snapshot.availableProcessors().isKnown());
		assertTrue(snapshot.heapUsedBytes().isKnown());
		assertTrue(snapshot.heapMaxBytes().isKnown());
		assertTrue(snapshot.heapUsedBytes().value() > 0);
	}

	@Test
	void neverReportsAnUnknownValueAsZero() {
		ResourceSnapshot snapshot = new SystemResourceProbe().probe();

		for (Measurement<?> measurement : new Measurement<?>[] {
				snapshot.systemMemoryTotalBytes(),
				snapshot.systemMemoryFreeBytes(),
				snapshot.systemLoadAverage() }) {
			if (!measurement.isKnown()) {
				assertFalse(measurement.confidence() == Confidence.MEASURED,
						"an unknown measurement must not claim to be measured");
			}
		}
	}

	@Test
	void theProbeIsSafeToCallRepeatedly() {
		SystemResourceProbe probe = new SystemResourceProbe();
		for (int i = 0; i < 50; i++) {
			assertNotNull(probe.probe());
		}
	}

	@Test
	void heapUsageNeverExceedsTheMaximumHeap() {
		ResourceSnapshot snapshot = new SystemResourceProbe().probe();
		assertTrue(snapshot.heapUsedBytes().value() <= snapshot.heapMaxBytes().value());
		assertEquals("jvm+os", snapshot.heapUsedBytes().source());
	}
}
