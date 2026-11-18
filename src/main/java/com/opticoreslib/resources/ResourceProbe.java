package com.opticoreslib.resources;

/**
 * Probe that produces a {@link ResourceSnapshot}.
 *
 * <p>Probes must be cheap and must never allocate large buffers. They are expected to be
 * invoked through a cached wrapper so that expensive work happens rarely.
 */
@FunctionalInterface
public interface ResourceProbe {

	/** Takes a snapshot. Implementations must not throw. */
	ResourceSnapshot probe();

	/** Identifier of the probe, used as the default source label. */
	default String source() {
		return getClass().getSimpleName();
	}
}
