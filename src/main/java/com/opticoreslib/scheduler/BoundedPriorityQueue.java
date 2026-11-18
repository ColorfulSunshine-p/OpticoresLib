package com.opticoreslib.scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.opticoreslib.core.Clock;
import com.opticoreslib.task.TaskDescriptor;

/**
 * Bounded priority queue used by {@link DefaultTaskScheduler}.
 *
 * <p>Ordering is by effective priority (highest first) and, inside one priority, by
 * submission sequence (oldest first), so the queue is stable and starvation free inside a
 * priority band.
 *
 * <p>The queue is not thread safe. The scheduler owns the lock that guards it.
 */
final class BoundedPriorityQueue {

	/** Outcome of an offer. */
	enum OfferResult {
		/** The task was added to the queue. */
		ACCEPTED,
		/** The task was merged into an equivalent queued task. */
		COALESCED,
		/** The queue is at capacity. */
		FULL
	}

	/** A queued task. */
	static final class Entry {

		private TaskDescriptor descriptor;
		private final long sequence;
		private final long enqueuedNanos;
		private final java.time.Instant submittedAt;
		private final String coalescingKey;
		private int effectivePriority;

		Entry(TaskDescriptor descriptor, String coalescingKey, long sequence, long enqueuedNanos,
				java.time.Instant submittedAt) {
			this.descriptor = descriptor;
			this.coalescingKey = coalescingKey;
			this.sequence = sequence;
			this.enqueuedNanos = enqueuedNanos;
			this.submittedAt = submittedAt;
			this.effectivePriority = descriptor.priority().ordinal();
		}

		TaskDescriptor descriptor() {
			return descriptor;
		}

		String coalescingKey() {
			return coalescingKey;
		}

		void replaceDescriptor(TaskDescriptor descriptor) {
			this.descriptor = descriptor;
			this.effectivePriority = descriptor.priority().ordinal();
		}

		long sequence() {
			return sequence;
		}

		long enqueuedNanos() {
			return enqueuedNanos;
		}

		java.time.Instant submittedAt() {
			return submittedAt;
		}

		int effectivePriority() {
			return effectivePriority;
		}
	}

	private final int capacity;
	private final Clock clock;
	private final AgingPolicy agingPolicy;
	private final long agingResolutionNanos;
	private final List<Entry> heap = new ArrayList<>();
	private final Map<String, Entry> coalescingIndex = new HashMap<>();
	private long lastRefreshNanos = Long.MIN_VALUE;

	BoundedPriorityQueue(int capacity, Clock clock, AgingPolicy agingPolicy, Duration agingResolution) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be > 0");
		}
		this.capacity = capacity;
		this.clock = clock;
		this.agingPolicy = agingPolicy;
		this.agingResolutionNanos = agingResolution == null ? 0L : agingResolution.toNanos();
	}

	int capacity() {
		return capacity;
	}

	int size() {
		return heap.size();
	}

	boolean isEmpty() {
		return heap.isEmpty();
	}

	boolean containsCoalescingKey(String key) {
		return key != null && coalescingIndex.containsKey(key);
	}

	OfferResult offer(TaskDescriptor descriptor, String coalescingKey, long sequence, long nowNanos) {
		if (coalescingKey != null) {
			Entry existing = coalescingIndex.get(coalescingKey);
			if (existing != null) {
				existing.replaceDescriptor(descriptor);
				return OfferResult.COALESCED;
			}
		}
		if (heap.size() >= capacity) {
			return OfferResult.FULL;
		}
		Entry entry = new Entry(descriptor, coalescingKey, sequence, nowNanos, clock.instant());
		heap.add(entry);
		siftUp(heap.size() - 1);
		if (coalescingKey != null) {
			coalescingIndex.put(coalescingKey, entry);
		}
		return OfferResult.ACCEPTED;
	}

	Entry poll(long nowNanos) {
		refreshOrder(nowNanos);
		if (heap.isEmpty()) {
			return null;
		}
		Entry root = heap.get(0);
		int last = heap.size() - 1;
		heap.set(0, heap.get(last));
		heap.remove(last);
		if (!heap.isEmpty()) {
			siftDown(0);
		}
		removeFromCoalescingIndex(root);
		return root;
	}

	/**
	 * Removes every entry matching the predicate and re-establishes the heap invariant.
	 *
	 * @return the number of removed entries
	 */
	int removeIf(Predicate<Entry> predicate) {
		int removed = 0;
		Iterator<Entry> iterator = heap.iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (predicate.test(entry)) {
				iterator.remove();
				removeFromCoalescingIndex(entry);
				removed++;
			}
		}
		if (removed > 0) {
			for (int i = heap.size() / 2 - 1; i >= 0; i--) {
				siftDown(i);
			}
		}
		return removed;
	}

	void clear() {
		heap.clear();
		coalescingIndex.clear();
	}

	/**
	 * Recomputes the effective priorities and re-establishes the heap invariant.
	 *
	 * <p>Refreshing is throttled: with aging enabled the ordering may be up to one
	 * resolution step stale, which keeps the cost bounded and predictable.
	 */
	private void refreshOrder(long nowNanos) {
		if (agingPolicy == null || agingResolutionNanos <= 0L) {
			return;
		}
		if (lastRefreshNanos != Long.MIN_VALUE && nowNanos - lastRefreshNanos < agingResolutionNanos) {
			return;
		}
		lastRefreshNanos = nowNanos;
		for (Entry entry : heap) {
			Duration waiting = Duration.ofNanos(Math.max(0L, nowNanos - entry.enqueuedNanos()));
			entry.effectivePriority = agingPolicy.effectivePriority(entry.descriptor().priority(), waiting).ordinal();
		}
		for (int i = heap.size() / 2 - 1; i >= 0; i--) {
			siftDown(i);
		}
	}

	private void siftUp(int index) {
		Entry entry = heap.get(index);
		while (index > 0) {
			int parent = (index - 1) >>> 1;
			Entry parentEntry = heap.get(parent);
			if (!higher(entry, parentEntry)) {
				break;
			}
			heap.set(index, parentEntry);
			index = parent;
		}
		heap.set(index, entry);
	}

	private void siftDown(int index) {
		int size = heap.size();
		Entry entry = heap.get(index);
		int half = size >>> 1;
		while (index < half) {
			int child = (index << 1) + 1;
			int right = child + 1;
			Entry best = heap.get(child);
			if (right < size && higher(heap.get(right), best)) {
				child = right;
				best = heap.get(right);
			}
			if (!higher(best, entry)) {
				break;
			}
			heap.set(index, best);
			index = child;
		}
		heap.set(index, entry);
	}

	private static boolean higher(Entry a, Entry b) {
		if (a.effectivePriority() != b.effectivePriority()) {
			return a.effectivePriority() > b.effectivePriority();
		}
		return a.sequence() < b.sequence();
	}

	private void removeFromCoalescingIndex(Entry entry) {
		String key = entry.coalescingKey();
		if (key != null) {
			coalescingIndex.remove(key, entry);
		}
	}
}
