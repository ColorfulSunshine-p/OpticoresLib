package com.opticoreslib.integration;

/**
 * Typed identifier of a capability an integration may provide.
 *
 * @param id stable, namespaced identifier, for example {@code "opticores.chunk_pipeline"}
 */
public record CapabilityKey(String id) {

	public CapabilityKey {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		id = id.trim();
	}

	/** Creates a capability key. */
	public static CapabilityKey of(String id) {
		return new CapabilityKey(id);
	}

	@Override
	public String toString() {
		return id;
	}
}
