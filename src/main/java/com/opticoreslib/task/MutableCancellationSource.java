package com.opticoreslib.task;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link CancellationSource} implementation.
 */
final class MutableCancellationSource implements CancellationSource {

	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final CancellationToken token;

	MutableCancellationSource() {
		this.token = new CancellationToken() {

			@Override
			public boolean isCancelled() {
				return cancelled.get();
			}

			@Override
			public CancellationRegistration onCancel(Runnable action) {
				if (action == null) {
					return CancellationRegistration.none();
				}
				if (cancelled.get()) {
					action.run();
					return CancellationRegistration.none();
				}
				listeners.add(action);
				return () -> listeners.remove(action);
			}

			@Override
			public String toString() {
				return "CancellationToken[" + (cancelled.get() ? "cancelled" : "active") + "]";
			}
		};
	}

	@Override
	public CancellationToken token() {
		return token;
	}

	@Override
	public boolean isCancelled() {
		return cancelled.get();
	}

	@Override
	public void cancel() {
		if (!cancelled.compareAndSet(false, true)) {
			return;
		}
		for (Runnable listener : listeners) {
			try {
				listener.run();
			} catch (RuntimeException ignored) {
				// A misbehaving listener must not prevent the remaining listeners from running.
			}
		}
		listeners.clear();
	}

	@Override
	public String toString() {
		return "CancellationSource[" + (cancelled.get() ? "cancelled" : "active") + "]";
	}
}
