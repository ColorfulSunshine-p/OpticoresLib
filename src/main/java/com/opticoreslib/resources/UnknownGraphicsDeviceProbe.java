package com.opticoreslib.resources;

import java.time.Instant;

/**
 * Probe used when no graphics context is available, for example on a dedicated server.
 *
 * <p>Every field is unknown. This is the honest answer: a server has no graphics device
 * to describe.
 */
public final class UnknownGraphicsDeviceProbe implements GraphicsDeviceProbe {

	@Override
	public GraphicsDeviceSnapshot probe() {
		return new GraphicsDeviceSnapshot(
				Instant.EPOCH,
				"",
				"",
				"",
				GraphicsApi.UNKNOWN,
				Measurement.unknown("no-graphics-context"),
				Measurement.unknown("no-graphics-context"),
				Measurement.unknown("no-graphics-context"),
				Measurement.unknown("no-graphics-context"),
				java.util.Set.of());
	}

	@Override
	public String source() {
		return "none";
	}
}
