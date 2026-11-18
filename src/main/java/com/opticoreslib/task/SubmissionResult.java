package com.opticoreslib.task;

/**
 * Typed result of a submission attempt.
 *
 * <p>The library never returns a bare boolean. A caller can always tell <em>why</em> a
 * submission did not happen and can react accordingly (retry, drop, fall back).
 *
 * @param status     the outcome
 * @param taskId     identifier of the submitted task
 * @param groupId    identifier of the group the task belongs to
 * @param queueDepth number of tasks queued in the scheduler after the submission
 * @param reason     human readable explanation, never {@code null}
 */
public record SubmissionResult(SubmissionStatus status, String taskId, String groupId, int queueDepth, String reason) {

	public SubmissionResult {
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		if (taskId == null) {
			taskId = "";
		}
		if (groupId == null) {
			groupId = "";
		}
		if (reason == null) {
			reason = "";
		}
	}

	/** {@code true} when the scheduler owns the task. */
	public boolean isAccepted() {
		return status.isAdmitted();
	}

	/** {@code true} when the submission was refused. */
	public boolean isRejected() {
		return status.isRejection();
	}

	/** {@code true} when the task was merged into an already queued equivalent. */
	public boolean isCoalesced() {
		return status == SubmissionStatus.COALESCED;
	}

	/** Creates an admitted result. */
	public static SubmissionResult accepted(SubmissionStatus status, String taskId, String groupId, int queueDepth) {
		return new SubmissionResult(status, taskId, groupId, queueDepth, "");
	}

	/** Creates a refused result. */
	public static SubmissionResult refused(SubmissionStatus status, String taskId, String groupId, String reason) {
		return new SubmissionResult(status, taskId, groupId, 0, reason);
	}
}
