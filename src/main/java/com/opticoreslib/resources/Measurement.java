package com.opticoreslib.resources;

import java.util.Optional;

/**
 * A value together with its provenance.
 *
 * <p>Every numeric fact exposed by the library is wrapped in a {@code Measurement} so that
 * a consumer can always tell a measured value apart from an estimate and from a value the
 * operating system simply does not expose.
 *
 * <p>An unknown measurement never carries a value. This is deliberate: reporting
 * {@code 0} bytes of dedicated VRAM would be a lie, while reporting "unknown" lets the
 * consumer degrade gracefully.
 *
 * @param <T> value type
 */
public record Measurement<T>(T value, Confidence confidence, String source) {

	public Measurement {
		if (confidence == null) {
			confidence = Confidence.UNKNOWN;
		}
		if (confidence == Confidence.UNKNOWN) {
			value = null;
		}
		if (source == null) {
			source = "unknown";
		}
	}

	/** A value read directly from a reliable source. */
	public static <T> Measurement<T> measured(T value, String source) {
		return new Measurement<>(value, Confidence.MEASURED, source);
	}

	/** A value derived from measured data. */
	public static <T> Measurement<T> estimated(T value, String source) {
		return new Measurement<>(value, Confidence.ESTIMATED, source);
	}

	/** A rough guess. */
	public static <T> Measurement<T> guessed(T value, String source) {
		return new Measurement<>(value, Confidence.GUESSED, source);
	}

	/** A value that is not available. */
	public static <T> Measurement<T> unknown(String source) {
		return new Measurement<>(null, Confidence.UNKNOWN, source);
	}

	/** {@code true} when a value is present. */
	public boolean isKnown() {
		return confidence.isKnown() && value != null;
	}

	/** The value, when known. */
	public Optional<T> asOptional() {
		return isKnown() ? Optional.of(value) : Optional.empty();
	}

	/** The value, when known, or the supplied fallback. */
	public T orElse(T fallback) {
		return isKnown() ? value : fallback;
	}
}
