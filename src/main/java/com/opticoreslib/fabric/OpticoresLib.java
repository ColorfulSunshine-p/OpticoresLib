package com.opticoreslib.fabric;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Common entrypoint of the library.
 *
 * <p>The entrypoint does almost nothing on purpose: it creates the runtime and installs
 * the shutdown hooks. No worker thread is started and no background service is enabled
 * until a consumer actually asks for one.
 */
public class OpticoresLib implements ModInitializer {

	/** Mod id of the library. */
	public static final String MOD_ID = "opticoreslib";

	/** Logger of the library. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final AtomicReference<LibraryRuntime> RUNTIME = new AtomicReference<>();

	@Override
	public void onInitialize() {
		if (!installRuntime(LibraryRuntime.create())) {
			return;
		}
		LOGGER.info("OptiCoresLib infrastructure ready");
		registerPlatformHooks();
	}

	/**
	 * Installs the runtime, unless one is already installed.
	 *
	 * <p>This only manages the state of the holder. It performs no platform work, so it can
	 * be exercised without a running Fabric Loader.
	 *
	 * @return {@code true} when this call installed the runtime
	 */
	static boolean installRuntime(LibraryRuntime runtime) {
		if (runtime == null) {
			throw new IllegalArgumentException("runtime must not be null");
		}
		return RUNTIME.compareAndSet(null, runtime);
	}

	/** Registers the platform specific shutdown hooks. */
	private static void registerPlatformHooks() {
		// On a dedicated server the process ends with the server, so the runtime is
		// released there. On a client the integrated server also stops whenever the player
		// leaves a world, which must NOT tear down the client side runtime; that is handled
		// by the client shutdown hook instead.
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
			ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown("server stopped"));
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown("jvm shutdown"), MOD_ID + "-shutdown"));
	}

	/** The active runtime, if the library has been initialised. */
	public static Optional<LibraryRuntime> runtime() {
		return Optional.ofNullable(RUNTIME.get());
	}

	/**
	 * The active runtime.
	 *
	 * @throws IllegalStateException when the library has not been initialised yet
	 */
	public static LibraryRuntime requireRuntime() {
		LibraryRuntime runtime = RUNTIME.get();
		if (runtime == null) {
			throw new IllegalStateException("OptiCoresLib has not been initialised");
		}
		return runtime;
	}

	/**
	 * Releases every library service and shuts the runtime down.
	 *
	 * <p>Safe to call from several shutdown paths and from several threads: the runtime is
	 * taken out atomically, so only the first caller actually closes it and the others
	 * return immediately.
	 *
	 * @param reason why the shutdown was triggered, used for the log message
	 */
	public static void shutdown(String reason) {
		LibraryRuntime runtime = RUNTIME.getAndSet(null);
		if (runtime == null) {
			return;
		}
		LOGGER.info("Shutting down OptiCoresLib infrastructure ({})", reason);
		runtime.close();
	}
}
