package com.opticoreslib.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MeasurementTest {

	@Test
	void unknownMeasurementsCarryNoValue() {
		Measurement<Long> measurement = Measurement.unknown("not-exposed");

		assertFalse(measurement.isKnown());
		assertNull(measurement.value());
		assertEquals(Confidence.UNKNOWN, measurement.confidence());
		assertTrue(measurement.asOptional().isEmpty());
	}

	@Test
	void measuredValuesKeepTheirProvenance() {
		Measurement<Long> measurement = Measurement.measured(42L, "jvm");

		assertTrue(measurement.isKnown());
		assertEquals(42L, measurement.value());
		assertEquals(Confidence.MEASURED, measurement.confidence());
		assertEquals("jvm", measurement.source());
		assertEquals(42L, measurement.orElse(1L));
	}

	@Test
	void estimatesAreDistinguishableFromMeasurements() {
		assertEquals(Confidence.ESTIMATED, Measurement.estimated(1L, "formula").confidence());
		assertEquals(Confidence.GUESSED, Measurement.guessed(1L, "vibes").confidence());
	}

	@Test
	void unknownFallsBackToTheSuppliedDefault() {
		assertEquals(7L, Measurement.<Long>unknown("x").orElse(7L));
	}

	@Test
	void graphicsSnapshotNeverClaimsSharedMemoryIsDedicated() {
		GraphicsDeviceSnapshot snapshot = new GraphicsDeviceSnapshot(
				Instant.EPOCH,
				"vendor",
				"renderer",
				"version",
				GraphicsApi.OPENGL,
				Measurement.unknown("opengl-does-not-expose-vram"),
				Measurement.measured(8_000_000_000L, "os"),
				Measurement.unknown("opengl-does-not-expose-vram"),
				Measurement.measured(16384, "gl"),
				java.util.Set.of());

		assertFalse(snapshot.hasKnownDedicatedMemory());
		assertFalse(snapshot.dedicatedMemoryBytes().isKnown());
		assertTrue(snapshot.sharedMemoryBytes().isKnown());
	}

	@Test
	void capabilitiesAreExposedSeparatelyFromNumbers() {
		GraphicsDeviceSnapshot snapshot = new GraphicsDeviceSnapshot(
				Instant.EPOCH,
				"",
				"",
				"",
				GraphicsApi.UNKNOWN,
				Measurement.unknown("x"),
				Measurement.unknown("x"),
				Measurement.unknown("x"),
				Measurement.unknown("x"),
				java.util.Set.of(GraphicsCapability.COMPUTE_SHADERS));

		assertTrue(snapshot.supports(GraphicsCapability.COMPUTE_SHADERS));
		assertFalse(snapshot.supports(GraphicsCapability.SPARSE_BINDING));
		assertEquals(1, snapshot.capabilities().size());
	}

	@Test
	void unknownGraphicsProbeReportsNothingButHonesty() {
		GraphicsDeviceSnapshot snapshot = new UnknownGraphicsDeviceProbe().probe();

		assertEquals(GraphicsApi.UNKNOWN, snapshot.api());
		assertFalse(snapshot.hasKnownDedicatedMemory());
		assertTrue(snapshot.capabilities().isEmpty());
	}
}
