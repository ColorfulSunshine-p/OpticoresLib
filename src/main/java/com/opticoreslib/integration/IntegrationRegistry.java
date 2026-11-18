package com.opticoreslib.integration;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of optional integrations.
 *
 * <p>The library itself registers nothing. A consumer registers the providers it wants,
 * which keeps the library free of any hard dependency on a third party mod.
 *
 * <p>Providers are started lazily and only when the version range matches. A provider
 * that fails is reported as {@link IntegrationState#FAILED} and never prevents the others
 * from starting.
 */
public final class IntegrationRegistry {

	private final Map<String, IntegrationProvider> providers = new ConcurrentHashMap<>();
	private final Map<String, String> details = new ConcurrentHashMap<>();

	/** Registers a provider. Registering an existing id replaces the previous provider. */
	public void register(IntegrationProvider provider) {
		if (provider == null) {
			throw new IllegalArgumentException("provider must not be null");
		}
		providers.put(provider.id(), provider);
	}

	/** Looks up a registered provider. */
	public Optional<IntegrationProvider> provider(String id) {
		return id == null ? Optional.empty() : Optional.ofNullable(providers.get(id));
	}

	/** All registered providers. */
	public Set<IntegrationProvider> providers() {
		return Set.copyOf(providers.values());
	}

	/** Capabilities declared by all registered providers. */
	public Set<CapabilityKey> capabilities() {
		Set<CapabilityKey> capabilities = new LinkedHashSet<>();
		for (IntegrationProvider provider : providers.values()) {
			capabilities.addAll(provider.capabilities());
		}
		return Set.copyOf(capabilities);
	}

	/** Providers that declare the given capability. */
	public Set<IntegrationProvider> providersFor(CapabilityKey capability) {
		Set<IntegrationProvider> matches = new LinkedHashSet<>();
		for (IntegrationProvider provider : providers.values()) {
			if (provider.capabilities().contains(capability)) {
				matches.add(provider);
			}
		}
		return Set.copyOf(matches);
	}

	/** Current state of every registered provider. */
	public Map<String, IntegrationState> states() {
		Map<String, IntegrationState> states = new ConcurrentHashMap<>();
		for (IntegrationProvider provider : providers.values()) {
			states.put(provider.id(), provider.state());
		}
		return Map.copyOf(states);
	}

	/** Detail message recorded for a provider, if any. */
	public Optional<String> detail(String id) {
		return id == null ? Optional.empty() : Optional.ofNullable(details.get(id));
	}

	/**
	 * Starts every registered provider whose version range matches the given version.
	 *
	 * @param hostVersion version of the host application
	 */
	public void startAll(IntegrationContext context, String hostVersion) {
		for (IntegrationProvider provider : providers.values()) {
			start(provider, context, hostVersion);
		}
	}

	/** Starts a single provider. */
	public void start(IntegrationProvider provider, IntegrationContext context, String hostVersion) {
		if (provider == null) {
			return;
		}
		if (provider.state() == IntegrationState.STARTED) {
			return;
		}
		if (!provider.supportedVersions().contains(hostVersion)) {
			details.put(provider.id(), "host version " + hostVersion + " is outside the supported range");
			return;
		}
		try {
			provider.start(context);
		} catch (RuntimeException e) {
			details.put(provider.id(), e.getClass().getSimpleName() + ": "
					+ (e.getMessage() == null ? "" : e.getMessage()));
		}
	}

	/** Stops every registered provider. */
	public void stopAll() {
		for (IntegrationProvider provider : providers.values()) {
			try {
				provider.stop();
			} catch (RuntimeException ignored) {
				// A failing provider must not prevent the others from stopping.
			}
		}
	}

	/** Removes every registered provider. */
	public void clear() {
		providers.clear();
		details.clear();
	}
}
