package com.opticoreslib.fabric;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.opticoreslib.core.Clock;
import com.opticoreslib.core.ManagedService;
import com.opticoreslib.core.ServiceRegistry;
import com.opticoreslib.core.ServiceState;
import com.opticoreslib.integration.IntegrationContext;
import com.opticoreslib.integration.IntegrationRegistry;
import com.opticoreslib.integration.IntegrationState;
import com.opticoreslib.metrics.MetricRegistry;
import com.opticoreslib.resources.GraphicsDeviceProbe;
import com.opticoreslib.scheduler.DefaultTaskScheduler;
import com.opticoreslib.scheduler.SchedulerOptions;
import com.opticoreslib.scheduler.TaskScheduler;
import com.opticoreslib.task.CompletionDispatcher;

/**
 * Container for the library services a consumer may use.
 *
 * <p>The runtime is deliberately lazy. A consumer that only wants the metric registry
 * never causes a worker thread to be created, and a consumer that never touches the
 * scheduler never starts one. This is what keeps the library free of background services
 * that a consumer did not ask for.
 *
 * <p>The runtime is created by the platform entrypoint and closed on shutdown. Consumers
 * obtain it through {@link OpticoresLib#runtime()}.
 */
public final class LibraryRuntime implements AutoCloseable {

	/** Namespace used for the library's own metrics. */
	public static final String NAMESPACE = "opticoreslib";

	private final Clock clock;
	private final MetricRegistry metrics;
	private final IntegrationRegistry integrations = new IntegrationRegistry();
	private final ServiceRegistry services = new ServiceRegistry();
	private final AtomicReference<TaskScheduler> scheduler = new AtomicReference<>();
	private final AtomicReference<GraphicsDeviceProbe> graphicsProbe = new AtomicReference<>();
	private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.NEW);

	private LibraryRuntime(Clock clock) {
		this.clock = clock;
		this.metrics = new MetricRegistry(NAMESPACE);
	}

	/** Creates a runtime backed by the system clock. */
	public static LibraryRuntime create() {
		return new LibraryRuntime(Clock.system());
	}

	/** Creates a runtime backed by the given clock. */
	public static LibraryRuntime create(Clock clock) {
		return new LibraryRuntime(clock == null ? Clock.system() : clock);
	}

	/** The clock used by the library. */
	public Clock clock() {
		return clock;
	}

	/** The library's metric registry. Always available, never starts a thread. */
	public MetricRegistry metrics() {
		return metrics;
	}

	/** The registry of optional integrations. */
	public IntegrationRegistry integrations() {
		return integrations;
	}

	/**
	 * The task scheduler.
	 *
	 * <p>The scheduler is created and started on first use. It is closed with the runtime.
	 */
	public TaskScheduler scheduler() {
		TaskScheduler current = scheduler.get();
		if (current != null) {
			return current;
		}
		if (state.get() == ServiceState.CLOSED) {
			throw new IllegalStateException("the runtime has been closed");
		}
		TaskScheduler created = new DefaultTaskScheduler(SchedulerOptions.defaults(), clock,
				CompletionDispatcher.direct());
		if (!scheduler.compareAndSet(null, created)) {
			created.close();
			return scheduler.get();
		}
		services.register(created);
		created.start();
		return created;
	}

	/** Installs a graphics device probe. Only the client platform calls this. */
	public void installGraphicsProbe(GraphicsDeviceProbe probe) {
		graphicsProbe.set(probe);
	}

	/** The installed graphics device probe, if any. */
	public Optional<GraphicsDeviceProbe> graphicsProbe() {
		return Optional.ofNullable(graphicsProbe.get());
	}

	/** Looks up a library service by type. */
	public <T> Optional<T> service(Class<T> type) {
		if (type == null) {
			return Optional.empty();
		}
		if (type.isInstance(metrics)) {
			return Optional.of(type.cast(metrics));
		}
		if (type.isInstance(integrations)) {
			return Optional.of(type.cast(integrations));
		}
		if (type.isInstance(clock)) {
			return Optional.of(type.cast(clock));
		}
		TaskScheduler current = scheduler.get();
		if (current != null && type.isInstance(current)) {
			return Optional.of(type.cast(current));
		}
		return Optional.empty();
	}

	/** Current lifecycle state of the runtime. */
	public ServiceState state() {
		return state.get();
	}

	/** Stops accepting work, drains and closes every service. Idempotent. */
	@Override
	public void close() {
		if (state.getAndSet(ServiceState.CLOSED) == ServiceState.CLOSED) {
			return;
		}
		integrations.stopAll();
		services.close();
		scheduler.set(null);
		graphicsProbe.set(null);
	}

	/** Builds the context handed to optional integrations. */
	public IntegrationContext integrationContext() {
		return new RuntimeIntegrationContext(this);
	}

	/** Integration context backed by the runtime's services. */
	private static final class RuntimeIntegrationContext implements IntegrationContext {

		private final LibraryRuntime runtime;

		private RuntimeIntegrationContext(LibraryRuntime runtime) {
			this.runtime = runtime;
		}

		@Override
		public Clock clock() {
			return runtime.clock;
		}

		@Override
		public <T> Optional<T> service(Class<T> type) {
			return runtime.service(type);
		}

		@Override
		public void report(IntegrationState state, String detail) {
			OpticoresLib.LOGGER.info("[integration] {} {}", state, detail == null ? "" : detail);
		}
	}

	/** Timeout used when the runtime drains services during shutdown. */
	public static Duration defaultDrainTimeout() {
		return Duration.ofSeconds(5);
	}

	/** All managed services owned by the runtime. */
	public java.util.List<ManagedService> services() {
		return services.services();
	}
}