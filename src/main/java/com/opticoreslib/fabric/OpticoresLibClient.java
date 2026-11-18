package com.opticoreslib.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import com.opticoreslib.fabric.client.LwjglGraphicsDeviceProbe;

/**
 * Client entrypoint of the library.
 *
 * <p>The client side only installs the graphics device probe and the client shutdown
 * hook. The probe is never executed during initialisation, so it can never stall the
 * startup; it runs on first use and its result is cached.
 */
public class OpticoresLibClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		OpticoresLib.runtime().ifPresent(runtime -> {
			runtime.installGraphicsProbe(new LwjglGraphicsDeviceProbe());
			OpticoresLib.LOGGER.info("OptiCoresLib client infrastructure ready");
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> OpticoresLib.shutdown("client stopping"));
	}
}
