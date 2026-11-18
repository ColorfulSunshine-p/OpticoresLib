package com.opticoreslib.task;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cancellable scope that groups related work, for example everything belonging to one
 * world or one chunk pipeline stage.
 *
 * <p>Cancelling the scope cancels every registered child source, which in turn cancels
 * every task that observed those sources. This is the mechanism a consumer uses to
 * invalidate all work associated with a session in one call.
 */
public final class CancellationScope implements CancellationSource {

	private final String name;
	private final MutableCancellationSource source = new MutableCancellationSource();
	private final Set<CancellationSource> children = ConcurrentHashMap.newKeySet();

	public CancellationScope(String name) {
		this.name = (name == null || name.isBlank()) ? "scope" : name.trim();
	}

	/** Creates a named scope. */
	public static CancellationScope named(String name) {
		return new CancellationScope(name);
	}

	/** Name of the scope. */
	public String name() {
		return name;
	}

	@Override
	public CancellationToken token() {
		return source.token();
	}

	@Override
	public boolean isCancelled() {
		return source.isCancelled();
	}

	@Override
	public void cancel() {
		source.cancel();
		for (CancellationSource child : children) {
			child.cancel();
		}
		children.clear();
	}

	/**
	 * Registers a child source so that it is cancelled together with this scope.
	 *
	 * @return the registered source
	 */
	public <T extends CancellationSource> T register(T child) {
		if (child == null) {
			throw new IllegalArgumentException("child must not be null");
		}
		if (source.isCancelled()) {
			child.cancel();
			return child;
		}
		children.add(child);
		return child;
	}

	/** Unregisters a child source. */
	public void unregister(CancellationSource child) {
		children.remove(child);
	}

	/** Number of currently registered children. */
	public int childCount() {
		return children.size();
	}

	@Override
	public String toString() {
		return "CancellationScope[" + name + (isCancelled() ? " cancelled" : " active") + "]";
	}
}
