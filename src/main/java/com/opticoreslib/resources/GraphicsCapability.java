package com.opticoreslib.resources;

/**
 * Optional graphics capabilities, reported independently of any numeric estimate.
 */
public enum GraphicsCapability {

	/** Compute shaders are available. */
	COMPUTE_SHADERS,
	/** Timestamp queries are available, which allows GPU timing. */
	TIMESTAMP_QUERIES,
	/** Shader storage buffers are available. */
	STORAGE_BUFFERS,
	/** Indirect draw calls are available. */
	INDIRECT_DRAW,
	/** Multi draw indirect is available. */
	MULTI_DRAW_INDIRECT,
	/** Debug output objects are available. */
	DEBUG_OUTPUT,
	/** S3TC or comparable texture compression is available. */
	TEXTURE_COMPRESSION,
	/** Sparse resource binding is available. */
	SPARSE_BINDING
}
