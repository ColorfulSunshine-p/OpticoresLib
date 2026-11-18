package com.opticoreslib.core;

import java.util.function.Supplier;

/**
 * Resolves a value from a {@link FeatureState}, falling back to a default when the
 * feature is unavailable.
 *
 * <p>This is the canonical "safe fallback" helper: every optional capability in the
 * library degrades through it instead of throwing.
 *
 * @param <T> value type
 */
@FunctionalInterface
public interface Fallback<T> {

	/** Resolves the value for the given state. */
	T resolve(FeatureState<T> state);

	/** Falls back to a fixed value. */
	static <T> Fallback<T> of(T defaultValue) {
		return state -> state.isEnabled() ? state.value().orElse(defaultValue) : defaultValue;
	}

	/** Falls back to a lazily computed value. */
	static <T> Fallback<T> of(Supplier<T> defaultValueSupplier) {
		return state -> state.isEnabled() ? state.value().orElseGet(defaultValueSupplier) : defaultValueSupplier.get();
	}
}
