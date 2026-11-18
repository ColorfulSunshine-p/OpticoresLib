package com.opticoreslib.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

	@Test
	void startsInRegistrationOrderAndClosesInReverseOrder() {
		List<String> events = new ArrayList<>();
		ServiceRegistry registry = new ServiceRegistry();
		registry.register(new RecordingService("first", events));
		registry.register(new RecordingService("second", events));

		registry.startAll();
		assertEquals(List.of("start:first", "start:second"), events);

		registry.close();
		assertEquals(List.of("start:first", "start:second",
				"stop:second", "stop:first",
				"drain:second", "drain:first",
				"close:second", "close:first"), events);
	}

	@Test
	void closeIsIdempotent() {
		List<String> events = new ArrayList<>();
		ServiceRegistry registry = new ServiceRegistry();
		registry.register(new RecordingService("only", events));

		registry.close();
		registry.close();

		assertEquals(1, events.stream().filter(event -> event.startsWith("close:")).count());
	}

	@Test
	void stateTransitionsAreForwardOnly() {
		List<String> events = new ArrayList<>();
		RecordingService service = new RecordingService("service", events);

		assertEquals(ServiceState.NEW, service.state());
		service.start();
		assertEquals(ServiceState.RUNNING, service.state());
		service.stopAccepting();
		assertEquals(ServiceState.STOPPING, service.state());
		service.close();
		assertEquals(ServiceState.CLOSED, service.state());
		assertTrue(ServiceState.CLOSED.isTerminal());
		assertFalse(ServiceState.RUNNING.isTerminal());
	}

	@Test
	void stopAcceptingIsSafeBeforeStart() {
		List<String> events = new ArrayList<>();
		RecordingService service = new RecordingService("service", events);
		service.stopAccepting();
		service.stopAccepting();
		assertEquals(ServiceState.STOPPING, service.state());
	}

	private static final class RecordingService implements ManagedService {

		private final String name;
		private final List<String> events;
		private ServiceState state = ServiceState.NEW;

		private RecordingService(String name, List<String> events) {
			this.name = name;
			this.events = events;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public ServiceState state() {
			return state;
		}

		@Override
		public void start() {
			state = ServiceState.RUNNING;
			events.add("start:" + name);
		}

		@Override
		public void stopAccepting() {
			if (state == ServiceState.NEW || state == ServiceState.RUNNING) {
				state = ServiceState.STOPPING;
				events.add("stop:" + name);
			}
		}

		@Override
		public void drain(Duration timeout) {
			events.add("drain:" + name);
		}

		@Override
		public void close() {
			state = ServiceState.CLOSED;
			events.add("close:" + name);
		}
	}
}
