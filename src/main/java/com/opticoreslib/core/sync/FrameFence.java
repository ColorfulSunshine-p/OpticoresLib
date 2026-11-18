package com.opticoreslib.core.sync;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One-shot completion fence for frame synchronisation.
 *
 * <p>Callers signal completion of a frame and other threads wait for it. Waiting is
 * implemented with a condition variable, never with a spin loop, so a waiting thread does
 * not consume CPU while the frame is still in flight.
 *
 * <p>Waiting always takes a timeout: an unbounded wait on a fence that is never signalled
 * would deadlock the caller.
 */
public final class FrameFence {

	private final Lock lock = new ReentrantLock();
	private final Condition completedCondition = lock.newCondition();
	private final Deque<Long> retired = new ArrayDeque<>();
	private long completedFrame = -1L;

	/** Marks a frame as completed. Out-of-order signals are tolerated. */
	public void signalComplete(long frameId) {
		lock.lock();
		try {
			if (frameId > completedFrame) {
				completedFrame = frameId;
			}
			retired.addLast(frameId);
			completedCondition.signalAll();
		} finally {
			lock.unlock();
		}
	}

	/** {@code true} when the given frame has been signalled. */
	public boolean isCompleted(long frameId) {
		lock.lock();
		try {
			return completedFrame >= frameId;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Waits until the given frame completes.
	 *
	 * @return {@code true} if the frame completed, {@code false} if the timeout elapsed first
	 */
	public boolean awaitCompletion(long frameId, Duration timeout) throws InterruptedException {
		long remaining = timeout.toNanos();
		lock.lock();
		try {
			while (completedFrame < frameId) {
				if (remaining <= 0L) {
					return false;
				}
				remaining = completedCondition.awaitNanos(remaining);
			}
			return true;
		} finally {
			lock.unlock();
		}
	}

	/** Waits indefinitely for the given frame. Prefer the timeout based overload. */
	public void awaitCompletion(long frameId) throws InterruptedException {
		lock.lock();
		try {
			while (completedFrame < frameId) {
				completedCondition.await();
			}
		} finally {
			lock.unlock();
		}
	}

	/** Waits for the given frame, swallowing interruptions. */
	public boolean awaitCompletionUninterruptibly(long frameId, Duration timeout) {
		boolean interrupted = false;
		try {
			while (true) {
				try {
					return awaitCompletion(frameId, timeout);
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/** Highest frame number that has been signalled, or {@code -1} when nothing completed yet. */
	public long completedFrame() {
		lock.lock();
		try {
			return completedFrame;
		} finally {
			lock.unlock();
		}
	}

	/** Removes and returns the oldest retired frame number. */
	public OptionalLong pollRetiredFrame() {
		lock.lock();
		try {
			Long frame = retired.pollFirst();
			return frame == null ? OptionalLong.empty() : OptionalLong.of(frame);
		} finally {
			lock.unlock();
		}
	}

	/** Number of retired frame notifications still queued. */
	public int retiredCount() {
		lock.lock();
		try {
			return retired.size();
		} finally {
			lock.unlock();
		}
	}
}
