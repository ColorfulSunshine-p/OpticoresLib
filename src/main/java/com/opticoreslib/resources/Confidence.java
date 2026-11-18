package com.opticoreslib.resources;

/**
 * How much trust a measurement deserves.
 */
public enum Confidence {

	/** Read directly from the operating system or driver. */
	MEASURED,
	/** Derived from measured data through a documented formula. */
	ESTIMATED,
	/** A rough guess with no reliable basis. */
	GUESSED,
	/** Not available. The value is unknown, not zero. */
	UNKNOWN;

	/** {@code true} when a usable value is present. */
	public boolean isKnown() {
		return this != UNKNOWN;
	}
}
