package com.opticoreslib.resources;

/**
 * Graphics backend a device is driven through.
 */
public enum GraphicsApi {

	/** Desktop OpenGL. */
	OPENGL,
	/** OpenGL ES. */
	OPENGL_ES,
	/** Vulkan. */
	VULKAN,
	/** Direct3D. */
	DIRECT3D,
	/** Metal. */
	METAL,
	/** The backend could not be determined. */
	UNKNOWN;

	/** {@code true} when the backend is known. */
	public boolean isKnown() {
		return this != UNKNOWN;
	}
}
