package com.opticoreslib.integration;

/**
 * Lifecycle state of an optional integration.
 */
public enum IntegrationState {

	/** Registered, but not started yet. */
	AVAILABLE,
	/** Running. */
	STARTED,
	/** The host application does not provide what the integration needs. */
	UNSUPPORTED,
	/** Present but switched off by the user or by configuration. */
	DISABLED,
	/** The integration failed while starting or running. */
	FAILED;

	/** {@code true} when the integration is usable. */
	public boolean isUsable() {
		return this == STARTED;
	}
}
