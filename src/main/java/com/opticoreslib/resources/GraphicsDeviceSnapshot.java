package com.opticoreslib.resources;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable snapshot of the graphics device.
 *
 * <p>Dedicated VRAM is reported only when the platform exposes it reliably. Integrated
 * GPUs usually share system memory, and reporting that shared memory as "dedicated VRAM"
 * would lead to hard allocation failures, so the value stays {@link Measurement#unknown}
 * unless a dedicated figure was genuinely measured.
 *
 * @param timestamp             wall-clock instant the snapshot was taken
 * @param vendor                device vendor, or an empty string when unknown
 * @param renderer              device renderer string, or an empty string when unknown
 * @param version               driver or API version string, or an empty string when unknown
 * @param api                   graphics backend
 * @param dedicatedMemoryBytes  dedicated VRAM, when reliably available
 * @param sharedMemoryBytes     memory shared with system RAM, when known
 * @param totalMemoryBytes      total device memory, when known
 * @param maxTextureSize        largest supported texture edge in texels, when known
 * @param capabilities          optional capabilities, never {@code null}
 */
public record GraphicsDeviceSnapshot(Instant timestamp,
		String vendor,
		String renderer,
		String version,
		GraphicsApi api,
		Measurement<Long> dedicatedMemoryBytes,
		Measurement<Long> sharedMemoryBytes,
		Measurement<Long> totalMemoryBytes,
		Measurement<Integer> maxTextureSize,
		Set<GraphicsCapability> capabilities) {

	public GraphicsDeviceSnapshot {
		if (timestamp == null) {
			timestamp = Instant.EPOCH;
		}
		if (vendor == null) {
			vendor = "";
		}
		if (renderer == null) {
			renderer = "";
		}
		if (version == null) {
			version = "";
		}
		if (api == null) {
			api = GraphicsApi.UNKNOWN;
		}
		if (dedicatedMemoryBytes == null) {
			dedicatedMemoryBytes = Measurement.unknown("graphics");
		}
		if (sharedMemoryBytes == null) {
			sharedMemoryBytes = Measurement.unknown("graphics");
		}
		if (totalMemoryBytes == null) {
			totalMemoryBytes = Measurement.unknown("graphics");
		}
		if (maxTextureSize == null) {
			maxTextureSize = Measurement.unknown("graphics");
		}
		capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
	}

	/** {@code true} when the given capability is available. */
	public boolean supports(GraphicsCapability capability) {
		return capabilities.contains(capability);
	}

	/**
	 * {@code true} only when a dedicated memory figure was genuinely measured.
	 *
	 * <p>Shared or system memory must never be reported as dedicated VRAM.
	 */
	public boolean hasKnownDedicatedMemory() {
		return dedicatedMemoryBytes.isKnown();
	}
}
