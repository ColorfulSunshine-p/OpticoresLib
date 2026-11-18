package com.opticoreslib.integration;

import java.util.Optional;

import com.opticoreslib.core.Clock;

/**
 * Context handed to an {@link IntegrationProvider} when it starts.
 *
 * <p>The context exposes library services through a service lookup instead of concrete
 * types, so an integration never has to depend on a specific implementation and the
 * library never leaks its internals.
 */
public interface IntegrationContext {

	/** The clock used by the library. */
	Clock clock();

	/** Looks up a library service, for example a metric registry. */
	<T> Optional<T> service(Class<T> type);

	/** Reports the state of the integration being started. */
	void report(IntegrationState state, String detail);
}
