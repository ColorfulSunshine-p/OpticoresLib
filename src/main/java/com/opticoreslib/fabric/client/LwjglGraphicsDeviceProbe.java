package com.opticoreslib.fabric.client;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import com.opticoreslib.resources.GraphicsApi;
import com.opticoreslib.resources.GraphicsCapability;
import com.opticoreslib.resources.GraphicsDeviceProbe;
import com.opticoreslib.resources.GraphicsDeviceSnapshot;
import com.opticoreslib.resources.Measurement;

/**
 * Graphics device probe backed by OpenGL.
 *
 * <p>Only cheap, allocation free queries are used: three strings and a single integer.
 * No buffer is allocated and no shader is compiled, so the probe cannot stall the render
 * thread noticeably.
 *
 * <p>OpenGL does not expose how much memory the device owns. Dedicated VRAM is therefore
 * reported as unknown instead of being guessed from system memory, which would report
 * shared memory of an integrated GPU as dedicated VRAM.
 *
 * <p>The probe must be called on the thread that owns the graphics context. Callers
 * should go through a cached wrapper so that the query happens rarely.
 */
public final class LwjglGraphicsDeviceProbe implements GraphicsDeviceProbe {

	private static final String SOURCE = "opengl";

	@Override
	public GraphicsDeviceSnapshot probe() {
		String vendor = safeString(GL11.GL_VENDOR);
		String renderer = safeString(GL11.GL_RENDERER);
		String version = safeString(GL11.GL_VERSION);
		GraphicsApi api = version != null && version.contains("OpenGL ES") ? GraphicsApi.OPENGL_ES : GraphicsApi.OPENGL;
		if (vendor == null && renderer == null && version == null) {
			api = GraphicsApi.UNKNOWN;
		}
		int[] glVersion = parseVersion(version);
		return new GraphicsDeviceSnapshot(
				Instant.now(),
				vendor == null ? "" : vendor,
				renderer == null ? "" : renderer,
				version == null ? "" : version,
				api,
				Measurement.unknown("opengl-does-not-expose-vram"),
				Measurement.unknown("opengl-does-not-expose-vram"),
				Measurement.unknown("opengl-does-not-expose-vram"),
				maxTextureSize(),
				capabilities(glVersion[0], glVersion[1]));
	}

	@Override
	public String source() {
		return SOURCE;
	}

	private static String safeString(int constant) {
		try {
			return GL11.glGetString(constant);
		} catch (Throwable ignored) {
			// Without a graphics context the driver may throw or crash the query.
			return null;
		}
	}

	private static Measurement<Integer> maxTextureSize() {
		try {
			return Measurement.measured(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), SOURCE);
		} catch (Throwable ignored) {
			return Measurement.unknown("no-graphics-context");
		}
	}

	/** Parses the leading {@code major.minor} of an OpenGL version string. */
	static int[] parseVersion(String version) {
		if (version == null) {
			return new int[] {0, 0};
		}
		int[] result = new int[] {0, 0};
		int index = 0;
		int[] parts = new int[2];
		int part = 0;
		while (index < version.length() && part < 2) {
			char current = version.charAt(index);
			if (current >= '0' && current <= '9') {
				int value = 0;
				while (index < version.length() && version.charAt(index) >= '0' && version.charAt(index) <= '9') {
					value = value * 10 + (version.charAt(index) - '0');
					index++;
				}
				parts[part++] = value;
			} else if (current == '.') {
				index++;
			} else {
				break;
			}
		}
		result[0] = parts[0];
		result[1] = parts[1];
		return result;
	}

	/** Derives the supported capabilities from the reported OpenGL version. */
	static Set<GraphicsCapability> capabilities(int major, int minor) {
		Set<GraphicsCapability> capabilities = EnumSet.noneOf(GraphicsCapability.class);
		if (major > 4 || (major == 4 && minor >= 3)) {
			capabilities.add(GraphicsCapability.COMPUTE_SHADERS);
			capabilities.add(GraphicsCapability.TIMESTAMP_QUERIES);
			capabilities.add(GraphicsCapability.DEBUG_OUTPUT);
		}
		if (major > 4 || (major == 4 && minor >= 5)) {
			capabilities.add(GraphicsCapability.STORAGE_BUFFERS);
			capabilities.add(GraphicsCapability.SPARSE_BINDING);
		}
		if (major >= 3) {
			capabilities.add(GraphicsCapability.INDIRECT_DRAW);
		}
		if (major >= 4 && minor >= 0) {
			capabilities.add(GraphicsCapability.MULTI_DRAW_INDIRECT);
		}
		return capabilities;
	}
}
