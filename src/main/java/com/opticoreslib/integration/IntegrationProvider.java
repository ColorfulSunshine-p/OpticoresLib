package com.opticoreslib.integration;

import java.util.Set;

/**
 * Contract for an optional integration.
 *
 * <p>The library ships the contract only. Concrete adapters for specific third party mods
 * live in the consumer that needs them, so that the library never takes a hard dependency
 * on a mod that most users do not have.
 *
 * <p>An implementation must be constructible without the optional mod being present. The
 * presence check happens at the platform edge; the provider itself is only loaded when the
 * consumer decides to load it.
 */
public interface IntegrationProvider {

	/** Stable identifier of the integration, for example the id of the mod it adapts. */
	String id();

	/**
	 * Version range of the host application this integration supports.
	 *
	 * <p>The range is checked before {@link #start(IntegrationContext)} is called, so an
	 * incompatible version is reported as {@link IntegrationState#UNSUPPORTED} instead of
	 * failing at runtime.
	 */
	VersionRange supportedVersions();

	/** Capabilities this integration provides. */
	Set<CapabilityKey> capabilities();

	/** Current state of the integration. */
	IntegrationState state();

	/** Starts the integration. Called only when it is supported and present. */
	void start(IntegrationContext context);

	/** Stops the integration. Must tolerate being called when it never started. */
	void stop();

	/** An inclusive version range. A {@code null} maximum means "no upper bound". */
	record VersionRange(String minimum, String maximum) {

		public VersionRange {
			if (minimum == null || minimum.isBlank()) {
				throw new IllegalArgumentException("minimum must not be blank");
			}
			minimum = minimum.trim();
			if (maximum != null) {
				maximum = maximum.isBlank() ? null : maximum.trim();
			}
		}

		/** A range that accepts exactly one version. */
		public static VersionRange exact(String version) {
			return new VersionRange(version, version);
		}

		/** A range with no upper bound. */
		public static VersionRange atLeast(String version) {
			return new VersionRange(version, null);
		}

		/** {@code true} when the given version falls into this range. */
		public boolean contains(String version) {
			if (version == null || version.isBlank()) {
				return false;
			}
			String candidate = version.trim();
			if (compare(candidate, minimum) < 0) {
				return false;
			}
			return maximum == null || compare(candidate, maximum) <= 0;
		}

		private static int compare(String left, String right) {
			String[] leftParts = left.split("[._-]");
			String[] rightParts = right.split("[._-]");
			int length = Math.max(leftParts.length, rightParts.length);
			for (int i = 0; i < length; i++) {
				String leftPart = i < leftParts.length ? leftParts[i] : "0";
				String rightPart = i < rightParts.length ? rightParts[i] : "0";
				int result;
				if (isNumeric(leftPart) && isNumeric(rightPart)) {
					result = Long.compare(Long.parseLong(leftPart), Long.parseLong(rightPart));
				} else {
					result = leftPart.compareToIgnoreCase(rightPart);
				}
				if (result != 0) {
					return result;
				}
			}
			return 0;
		}

		private static boolean isNumeric(String value) {
			if (value.isEmpty()) {
				return false;
			}
			for (int i = 0; i < value.length(); i++) {
				if (!Character.isDigit(value.charAt(i))) {
					return false;
				}
			}
			return true;
		}
	}
}
