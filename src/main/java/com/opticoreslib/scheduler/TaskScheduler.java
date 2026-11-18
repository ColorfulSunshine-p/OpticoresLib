package com.opticoreslib.scheduler;

import java.util.Optional;

import com.opticoreslib.core.ManagedService;
import com.opticoreslib.task.SubmissionResult;
import com.opticoreslib.task.Task;
import com.opticoreslib.task.TaskDescriptor;
import com.opticoreslib.task.TaskGroup;
import com.opticoreslib.task.TaskGroupStats;

/**
 * Accepts bounded background work and runs it on a managed pool of workers.
 *
 * <p>Submissions always produce a typed {@link SubmissionResult}. The scheduler never
 * silently drops work and never accepts more than its configured bound.
 */
public interface TaskScheduler extends ManagedService {

	/**
	 * Submits a task.
	 *
	 * <p>The call never blocks on the task itself and never throws for ordinary refusal
	 * conditions; those are reported through the returned result.
	 */
	SubmissionResult submit(TaskDescriptor descriptor);

	/** Submits a task with default metadata. */
	default SubmissionResult submit(Task task) {
		return submit(TaskDescriptor.builder(task).build());
	}

	/** Current counters of the scheduler. */
	SchedulerStats stats();

	/** Registers a task group. Registering an existing id replaces the group. */
	TaskGroup registerGroup(TaskGroup group);

	/** Looks up a registered group. */
	Optional<TaskGroup> group(String id);

	/** Removes a registered group. Returns {@code true} when a group was removed. */
	boolean unregisterGroup(String id);

	/** Runtime counters of a group. */
	Optional<TaskGroupStats> groupStats(String id);

	/** Number of tasks waiting in the scheduler. */
	int queueDepth();
}
