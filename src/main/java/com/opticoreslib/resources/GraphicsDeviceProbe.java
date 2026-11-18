package com.opticoreslib.resources;

/**
 * Probe that produces a {@link GraphicsDeviceSnapshot}.
 *
 * <p>Implementations live at the platform edge because they need a graphics context. The
 * core API only knows the interface, which keeps the version neutral modules free of any
 * rendering dependency.
 */
@FunctionalInterface
public interface GraphicsDeviceProbe {

	/** Takes a snapshot. Implementations must not throw. */
	GraphicsDeviceSnapshot probe();

	/** Identifier of the probe, used as the default source label. */
	default String source() {
		return getClass().getSimpleName();
	}
}
