package com.opticoreslib.core;

import java.util.Optional;

/**
 * Result of probing an optional capability: either a usable value or an explicit failure
 * reason.
 *
 * <p>A missing value is never silently represented as a default. It always carries the
 * reason it is missing so that consumers can degrade gracefully and report honestly.
 *
 * @param <T> type of the resolved value
 */
public interface FeatureState<T> {

	/** The resolved value, if the feature is available. */
	Optional<T> value();

	/** Why the feature is unavailable, if it is. */
	FailureReason failure();

	/** {@code true} when a value is present. */
	boolean isEnabled();

	/** Reason a feature could not be resolved. */
	record FailureReason(String code, String message) {

		public FailureReason {
			if (code == null || code.isBlank()) {
				throw new IllegalArgumentException("code must not be blank");
			}
			if (message == null) {
				message = "";
			}
		}
	}

	/** A feature that resolved successfully. */
	static <T> FeatureState<T> enabled(T value) {
		if (value == null) {
			throw new IllegalArgumentException("an enabled feature must carry a value");
		}
		return new State<>(Optional.of(value), null);
	}

	/** A feature that could not be resolved. */
	static <T> FeatureState<T> disabled(String code, String message) {
		return new State<>(Optional.empty(), new FailureReason(code, message));
	}

	/** Default implementation. */
	record State<T>(Optional<T> value, FailureReason failure) implements FeatureState<T> {

		@Override
		public boolean isEnabled() {
			return value.isPresent();
		}
	}
}
