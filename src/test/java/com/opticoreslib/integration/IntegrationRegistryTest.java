package com.opticoreslib.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.opticoreslib.core.Clock;

class IntegrationRegistryTest {

	@Test
	void startsOnlyProvidersWhoseVersionRangeMatches() {
		IntegrationRegistry registry = new IntegrationRegistry();
		StubProvider matching = new StubProvider("matching", IntegrationProvider.VersionRange.exact("1.21.1"));
		StubProvider other = new StubProvider("other", IntegrationProvider.VersionRange.exact("1.20.1"));
		registry.register(matching);
		registry.register(other);

		registry.startAll(context(), "1.21.1");

		assertEquals(IntegrationState.STARTED, matching.state());
		assertFalse(other.started.get());
		assertEquals(IntegrationState.AVAILABLE, other.state());
		assertEquals(2, registry.states().size());
		assertEquals(IntegrationState.STARTED, registry.states().get("matching"));
	}

	@Test
	void exposesDeclaredCapabilities() {
		IntegrationRegistry registry = new IntegrationRegistry();
		CapabilityKey key = CapabilityKey.of("example.pipeline");
		registry.register(new StubProvider("provider", IntegrationProvider.VersionRange.atLeast("1.0"), key));

		assertEquals(Set.of(key), registry.capabilities());
		assertEquals(1, registry.providersFor(key).size());
	}

	@Test
	void aFailingProviderDoesNotStopTheOthers() {
		IntegrationRegistry registry = new IntegrationRegistry();
		StubProvider failing = new StubProvider("failing", IntegrationProvider.VersionRange.atLeast("1.0"));
		failing.failOnStart = true;
		StubProvider healthy = new StubProvider("healthy", IntegrationProvider.VersionRange.atLeast("1.0"));
		registry.register(failing);
		registry.register(healthy);

		registry.startAll(context(), "1.21.1");

		assertTrue(healthy.started.get());
		assertTrue(registry.detail("failing").isPresent());
	}

	@Test
	void stopsEveryProvider() {
		IntegrationRegistry registry = new IntegrationRegistry();
		StubProvider provider = new StubProvider("provider", IntegrationProvider.VersionRange.atLeast("1.0"));
		registry.register(provider);
		registry.startAll(context(), "1.21.1");

		registry.stopAll();

		assertTrue(provider.stopped.get());
	}

	@Test
	void versionRangeComparesNumerically() {
		IntegrationProvider.VersionRange range = new IntegrationProvider.VersionRange("1.21.1", "1.21.11");

		assertTrue(range.contains("1.21.1"));
		assertTrue(range.contains("1.21.5"));
		assertTrue(range.contains("1.21.11"));
		assertFalse(range.contains("1.21.12"));
		assertFalse(range.contains("1.20.6"));
		assertFalse(range.contains(null));
	}

	private static IntegrationContext context() {
		return new IntegrationContext() {

			@Override
			public Clock clock() {
				return Clock.system();
			}

			@Override
			public <T> java.util.Optional<T> service(Class<T> type) {
				return java.util.Optional.empty();
			}

			@Override
			public void report(IntegrationState state, String detail) {
			}
		};
	}

	private static final class StubProvider implements IntegrationProvider {

		private final String id;
		private final VersionRange range;
		private final Set<CapabilityKey> capabilities;
		private final AtomicBoolean started = new AtomicBoolean();
		private final AtomicBoolean stopped = new AtomicBoolean();
		private final AtomicInteger reports = new AtomicInteger();
		private boolean failOnStart;
		private IntegrationState state = IntegrationState.AVAILABLE;

		private StubProvider(String id, VersionRange range, CapabilityKey... capabilities) {
			this.id = id;
			this.range = range;
			this.capabilities = Set.of(capabilities);
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public VersionRange supportedVersions() {
			return range;
		}

		@Override
		public Set<CapabilityKey> capabilities() {
			return capabilities;
		}

		@Override
		public IntegrationState state() {
			return state;
		}

		@Override
		public void start(IntegrationContext context) {
			if (failOnStart) {
				state = IntegrationState.FAILED;
				throw new IllegalStateException("boom");
			}
			started.set(true);
			state = IntegrationState.STARTED;
		}

		@Override
		public void stop() {
			stopped.set(true);
			reports.incrementAndGet();
		}
	}
}
