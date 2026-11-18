package com.opticoreslib.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.opticoreslib.concurrent.BoundedManagedExecutor;
import com.opticoreslib.concurrent.ManagedExecutor;
import com.opticoreslib.concurrent.ManagedExecutorConfig;
import com.opticoreslib.concurrent.RejectionStrategy;
import com.opticoreslib.core.Clock;
import com.opticoreslib.core.ServiceState;
import com.opticoreslib.scheduler.BoundedPriorityQueue.Entry;
import com.opticoreslib.scheduler.BoundedPriorityQueue.OfferResult;
import com.opticoreslib.task.CompletionDispatcher;
import com.opticoreslib.task.SubmissionResult;
import com.opticoreslib.task.SubmissionStatus;
import com.opticoreslib.task.TaskContext;
import com.opticoreslib.task.TaskDescriptor;
import com.opticoreslib.task.TaskGroup;
import com.opticoreslib.task.TaskGroupStats;
import com.opticoreslib.task.TaskResult;

/**
 * Default {@link TaskScheduler}.
 *
 * <p>Design rules that this implementation upholds:
 * <ul>
 *   <li>The queue is bounded and the bound is always enforced.</li>
 *   <li>Workers block on a condition variable; there is no busy waiting.</li>
 *   <li>Cancelled tasks are removed instead of accumulating.</li>
 *   <li>Every submission produces a typed outcome.</li>
 *   <li>Shutdown is deterministic: stop accepting, drain, then release the workers.</li>
 * </ul>
 */
public final class DefaultTaskScheduler implements TaskScheduler {

	private static final long AWAIT_SLICE_MILLIS = 25L;

	private final SchedulerOptions options;
	private final Clock clock;
	private final CompletionDispatcher completion;
	private final BoundedPriorityQueue queue;
	private final ManagedExecutor workers;
	private final Map<String, GroupState> groups = new ConcurrentHashMap<>();

	private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.NEW);
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicInteger active = new AtomicInteger();
	private final LongAdder completed = new LongAdder();
	private final LongAdder failed = new LongAdder();
	private final LongAdder cancelled = new LongAdder();
	private final LongAdder rejected = new LongAdder();
	private final LongAdder coalesced = new LongAdder();
	private final LongAdder deferred = new LongAdder();
	private final LongAdder totalWaitNanos = new LongAdder();
	private final AtomicLong maxWaitNanos = new AtomicLong();

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition workAvailable = lock.newCondition();
	private volatile boolean running;

	public DefaultTaskScheduler(SchedulerOptions options, Clock clock, CompletionDispatcher completion) {
		if (options == null) {
			throw new IllegalArgumentException("options must not be null");
		}
		this.options = options;
		this.clock = clock == null ? Clock.system() : clock;
		this.completion = completion == null ? CompletionDispatcher.direct() : completion;
		this.queue = new BoundedPriorityQueue(options.queueCapacity(), this.clock, options.agingPolicy(),
				options.agingResolution());
		ManagedExecutorConfig workerConfig = ManagedExecutorConfig
				.builder("opticoreslib-scheduler")
				.maxConcurrency(options.workerThreads())
				.queueCapacity(options.workerThreads())
				.rejectionStrategy(RejectionStrategy.ABORT)
				.daemon(options.daemonWorkers())
				.build();
		this.workers = new BoundedManagedExecutor(workerConfig);
	}

	@Override
	public String name() {
		return "opticoreslib-scheduler";
	}

	@Override
	public ServiceState state() {
		return state.get();
	}

	@Override
	public void start() {
		ServiceState current = state.get();
		if (current == ServiceState.RUNNING) {
			return;
		}
		if (!state.compareAndSet(ServiceState.NEW, ServiceState.STARTING)) {
			throw new IllegalStateException("scheduler cannot be started from state " + state.get());
		}
		running = true;
		try {
			for (int i = 0; i < options.workerThreads(); i++) {
				workers.execute(this::workerLoop);
			}
		} catch (RejectedExecutionException e) {
			state.set(ServiceState.FAILED);
			running = false;
			throw e;
		}
		state.set(ServiceState.RUNNING);
	}

	@Override
	public void stopAccepting() {
		ServiceState current = state.get();
		if (current == ServiceState.STOPPING || current == ServiceState.STOPPED || current == ServiceState.CLOSED) {
			return;
		}
		state.set(ServiceState.STOPPING);
		lock.lock();
		try {
			running = false;
			workAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void drain(Duration timeout) {
		if (timeout == null || timeout.isNegative()) {
			return;
		}
		long deadline = clock.nanoTime() + timeout.toNanos();
		lock.lock();
		try {
			while ((!queue.isEmpty() || active.get() > 0) && clock.nanoTime() < deadline) {
				workAvailable.await(AWAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() {
		stopAccepting();
		drain(options.shutdownTimeout());
		state.set(ServiceState.CLOSED);
		workers.close();
	}

	@Override
	public SubmissionResult submit(TaskDescriptor descriptor) {
		if (descriptor == null) {
			throw new NullPointerException("descriptor");
		}
		if (state.get() != ServiceState.RUNNING) {
			rejected.increment();
			return SubmissionResult.refused(SubmissionStatus.REJECTED_CLOSED, descriptor.id(), descriptor.groupId(),
					"scheduler is not accepting work");
		}
		if (descriptor.cancellationToken().isCancelled()) {
			cancelled.increment();
			return SubmissionResult.refused(SubmissionStatus.CANCELLED, descriptor.id(), descriptor.groupId(),
					"task was already cancelled");
		}
		if (options.backpressure().isSaturated()) {
			deferred.increment();
			return SubmissionResult.refused(SubmissionStatus.DEFERRED, descriptor.id(), descriptor.groupId(),
					"scheduler is under backpressure");
		}

		GroupState groupState = groupState(descriptor.groupId());
		if (groupState.group.isCancelled()) {
			cancelled.increment();
			return SubmissionResult.refused(SubmissionStatus.CANCELLED, descriptor.id(), descriptor.groupId(),
					"task group '" + groupState.group.id() + "' was cancelled");
		}

		String coalescingKey = coalescingKey(descriptor);
		lock.lock();
		try {
			boolean mergeCandidate = queue.containsCoalescingKey(coalescingKey);
			if (!mergeCandidate && groupState.queued.get() + groupState.pending.size() >= groupState.group.maxQueued()) {
				rejected.increment();
				groupState.rejected.increment();
				return SubmissionResult.refused(SubmissionStatus.REJECTED_CAPACITY, descriptor.id(),
						descriptor.groupId(), "group '" + groupState.group.id() + "' queue bound reached");
			}
			if (groupState.active.get() >= groupState.group.maxConcurrent()) {
				groupState.pending.addLast(descriptor);
				return SubmissionResult.accepted(SubmissionStatus.QUEUED, descriptor.id(), descriptor.groupId(),
						queue.size());
			}
			OfferResult result = queue.offer(descriptor, coalescingKey, sequence.incrementAndGet(), clock.nanoTime());
			if (result == OfferResult.FULL) {
				rejected.increment();
				groupState.rejected.increment();
				return SubmissionResult.refused(SubmissionStatus.REJECTED_CAPACITY, descriptor.id(),
						descriptor.groupId(), "scheduler queue is full");
			}
			if (result == OfferResult.COALESCED) {
				coalesced.increment();
				return SubmissionResult.accepted(SubmissionStatus.COALESCED, descriptor.id(), descriptor.groupId(),
						queue.size());
			}
			groupState.queued.incrementAndGet();
			workAvailable.signalAll();
			return SubmissionResult.accepted(SubmissionStatus.ACCEPTED, descriptor.id(), descriptor.groupId(),
					queue.size());
		} finally {
			lock.unlock();
		}
	}

	@Override
	public SchedulerStats stats() {
		return new SchedulerStats(
				queue.size(),
				active.get(),
				queue.capacity(),
				options.workerThreads(),
				completed.sum(),
				failed.sum(),
				cancelled.sum(),
				rejected.sum(),
				coalesced.sum(),
				deferred.sum(),
				totalWaitNanos.sum(),
				maxWaitNanos.get());
	}

	@Override
	public TaskGroup registerGroup(TaskGroup group) {
		if (group == null) {
			throw new IllegalArgumentException("group must not be null");
		}
		GroupState groupState = new GroupState(group);
		groups.put(group.id(), groupState);
		group.cancellationToken().onCancel(() -> purgeGroup(group.id()));
		return group;
	}

	@Override
	public Optional<TaskGroup> group(String id) {
		if (id == null) {
			return Optional.empty();
		}
		GroupState groupState = groups.get(id);
		return groupState == null ? Optional.empty() : Optional.of(groupState.group);
	}

	@Override
	public boolean unregisterGroup(String id) {
		if (id == null) {
			return false;
		}
		if (!groups.containsKey(id)) {
			return false;
		}
		purgeGroup(id);
		return groups.remove(id) != null;
	}

	@Override
	public Optional<TaskGroupStats> groupStats(String id) {
		if (id == null) {
			return Optional.empty();
		}
		GroupState groupState = groups.get(id);
		if (groupState == null) {
			return Optional.empty();
		}
		lock.lock();
		try {
			return Optional.of(new TaskGroupStats(
					groupState.queued.get() + groupState.pending.size(),
					groupState.active.get(),
					groupState.completed.sum(),
					groupState.failed.sum(),
					groupState.cancelled.sum(),
					groupState.rejected.sum()));
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int queueDepth() {
		return queue.size();
	}

	/** Number of worker threads currently alive. */
	public int workerCount() {
		return workers.stats().poolSize();
	}

	/** Worker pool statistics of the internal pool. */
	public com.opticoreslib.concurrent.WorkerStats workerStats() {
		return workers.stats();
	}

	private GroupState groupState(String groupId) {
		GroupState existing = groups.get(groupId);
		if (existing != null) {
			return existing;
		}
		return groups.computeIfAbsent(groupId, id -> {
			TaskGroup group = TaskGroup.of(id, options.defaultGroupMaxQueued(), Integer.MAX_VALUE);
			return new GroupState(group);
		});
	}

	private static String coalescingKey(TaskDescriptor descriptor) {
		String key = descriptor.coalescingKey();
		return key == null ? null : descriptor.groupId() + "/" + key;
	}

	/** Drops every pending task of a group. */
	private void purgeGroup(String groupId) {
		lock.lock();
		try {
			GroupState groupState = groups.get(groupId);
			if (groupState == null) {
				return;
			}
			int dropped = 0;
			for (TaskDescriptor descriptor : groupState.pending) {
				dropped++;
			}
			if (dropped > 0) {
				groupState.pending.clear();
				cancelled.add(dropped);
				groupState.cancelled.add(dropped);
			}
			workAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

	private void workerLoop() {
		while (true) {
			Entry entry = takeNext();
			if (entry == null) {
				if (!running) {
					return;
				}
				continue;
			}
			execute(entry);
		}
	}

	/** Takes the next task, or {@code null} when the scheduler is stopping and idle. */
	private Entry takeNext() {
		lock.lock();
		try {
			while (queue.isEmpty()) {
				if (!running) {
					return null;
				}
				try {
					workAvailable.await(AWAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
			return queue.poll(clock.nanoTime());
		} finally {
			lock.unlock();
		}
	}

	private void execute(Entry entry) {
		TaskDescriptor descriptor = entry.descriptor();
		GroupState groupState = groups.get(descriptor.groupId());

		if (descriptor.cancellationToken().isCancelled() || (groupState != null && groupState.group.isCancelled())) {
			finishCancelled(entry, groupState);
			return;
		}

		long startedNanos = clock.nanoTime();
		TaskContext context = new TaskContext(
				descriptor.id(),
				descriptor.groupId(),
				descriptor.priority(),
				entry.submittedAt(),
				Instant.now(),
				entry.sequence(),
				descriptor.cancellationToken(),
				descriptor.attributes());

		lock.lock();
		try {
			if (groupState != null) {
				groupState.queued.decrementAndGet();
				groupState.active.incrementAndGet();
			}
		} finally {
			lock.unlock();
		}
		active.incrementAndGet();

		TaskResult result;
		try {
			Object value = descriptor.task().execute(context);
			result = new TaskResult.Success(descriptor.id(), Duration.ofNanos(clock.nanoTime() - startedNanos), value);
			completed.increment();
			if (groupState != null) {
				groupState.completed.increment();
			}
		} catch (Throwable throwable) {
			result = new TaskResult.Failure(descriptor.id(), Duration.ofNanos(clock.nanoTime() - startedNanos),
					throwable.getClass().getName(), throwable.getMessage(), throwable);
			failed.increment();
			if (groupState != null) {
				groupState.failed.increment();
			}
		} finally {
			active.decrementAndGet();
			lock.lock();
			try {
				if (groupState != null) {
					groupState.active.decrementAndGet();
				}
			} finally {
				lock.unlock();
			}
			long wait = Math.max(0L, startedNanos - entry.enqueuedNanos());
			totalWaitNanos.add(wait);
			maxWaitNanos.accumulateAndGet(wait, Math::max);
		}

		dispatchCompletion(descriptor, context, result);
		promotePending(groupState);
	}

	private void dispatchCompletion(TaskDescriptor descriptor, TaskContext context, TaskResult result) {
		if (descriptor.completion() == null) {
			return;
		}
		completion.dispatch(() -> {
			try {
				descriptor.completion().accept(context, result);
			} catch (RuntimeException ignored) {
				// A failing completion callback must never kill a worker thread.
			}
		});
	}

	private void finishCancelled(Entry entry, GroupState groupState) {
		cancelled.increment();
		lock.lock();
		try {
			if (groupState != null) {
				groupState.queued.decrementAndGet();
				groupState.cancelled.increment();
			}
		} finally {
			lock.unlock();
		}
	}

	/** Moves tasks that were held back by a group concurrency limit back into the queue. */
	private void promotePending(GroupState groupState) {
		if (groupState == null) {
			return;
		}
		lock.lock();
		try {
			while (!groupState.pending.isEmpty() && groupState.active.get() < groupState.group.maxConcurrent()) {
				TaskDescriptor descriptor = groupState.pending.pollFirst();
				if (descriptor.cancellationToken().isCancelled() || groupState.group.isCancelled()) {
					cancelled.increment();
					groupState.cancelled.increment();
					continue;
				}
				String coalescingKey = coalescingKey(descriptor);
				OfferResult result = queue.offer(descriptor, coalescingKey, sequence.incrementAndGet(), clock.nanoTime());
				if (result == OfferResult.FULL) {
					groupState.pending.addFirst(descriptor);
					break;
				}
				if (result == OfferResult.COALESCED) {
					coalesced.increment();
					continue;
				}
				groupState.queued.incrementAndGet();
				workAvailable.signalAll();
			}
			workAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

	/** Per group runtime state. */
	private static final class GroupState {

		private final TaskGroup group;
		private final AtomicInteger queued = new AtomicInteger();
		private final AtomicInteger active = new AtomicInteger();
		private final LongAdder completed = new LongAdder();
		private final LongAdder failed = new LongAdder();
		private final LongAdder cancelled = new LongAdder();
		private final LongAdder rejected = new LongAdder();
		private final Deque<TaskDescriptor> pending = new ArrayDeque<>();

		private GroupState(TaskGroup group) {
			this.group = group;
		}
	}
}
