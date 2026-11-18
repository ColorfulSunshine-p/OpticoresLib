package com.opticoreslib.task;

/**
 * Internal helpers for {@link CancellationToken}.
 */
final class CancellationTokens {

	/** Shared token that never cancels. */
	static final CancellationToken NONE = new CancellationToken() {

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public CancellationRegistration onCancel(Runnable action) {
			return CancellationRegistration.none();
		}

		@Override
		public String toString() {
			return "CancellationToken[none]";
		}
	};

	private CancellationTokens() {
	}
}
