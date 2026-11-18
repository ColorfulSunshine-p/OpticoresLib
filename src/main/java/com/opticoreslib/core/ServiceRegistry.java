package com.opticoreslib.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ordered registry of {@link ManagedService} instances providing deterministic startup
 * and shutdown.
 *
 * <p>Services start in registration order and close in reverse registration order, which
 * keeps dependency ordering explicit: register the services you depend on first.
 *
 * <p>A failure while starting one service does not silently continue; the registry stops
 * and re-throws, leaving already started services running so the caller can decide how to
 * recover.
 */
public final class ServiceRegistry implements AutoCloseable {

	private final List<ManagedService> services = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	/** Registers a service and returns it, allowing fluent construction. */
	public <T extends ManagedService> T register(T service) {
		if (service == null) {
			throw new IllegalArgumentException("service must not be null");
		}
		services.add(service);
		return service;
	}

	/** All registered services, in registration order. */
	public List<ManagedService> services() {
		return List.copyOf(services);
	}

	/** Starts every service that has not been started yet, in registration order. */
	public void startAll() {
		for (ManagedService service : services) {
			if (service.state() == ServiceState.NEW || service.state() == ServiceState.STOPPED) {
				service.start();
			}
		}
	}

	/** Stops every service from accepting work, in reverse registration order. */
	public void stopAcceptingAll() {
		for (int i = services.size() - 1; i >= 0; i--) {
			services.get(i).stopAccepting();
		}
	}

	/** Drains every service, in reverse registration order. */
	public void drainAll(Duration timeout) {
		for (int i = services.size() - 1; i >= 0; i--) {
			services.get(i).drain(timeout);
		}
	}

	/**
	 * Stops accepting work, drains and closes every service in reverse registration order.
	 * Idempotent.
	 */
	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		stopAcceptingAll();
		drainAll(Duration.ofSeconds(5));
		for (int i = services.size() - 1; i >= 0; i--) {
			try {
				services.get(i).close();
			} catch (RuntimeException ignored) {
				// A failing service must not prevent the remaining services from closing.
			}
		}
	}
}
