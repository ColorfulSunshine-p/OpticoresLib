package com.opticoreslib.task;

/**
 * Outcome of a submission attempt.
 */
public enum SubmissionStatus {

	/** Admitted and eligible for execution. */
	ACCEPTED,
	/** Admitted, but held back because the group is at its concurrency limit. */
	QUEUED,
	/** Merged into an equivalent task that is already queued. */
	COALESCED,
	/** Not admitted because the scheduler is under backpressure. Retry later. */
	DEFERRED,
	/** Not admitted because a queue bound was reached. */
	REJECTED_CAPACITY,
	/** Not admitted because the scheduler is no longer accepting work. */
	REJECTED_CLOSED,
	/** Not admitted because the task was already cancelled. */
	CANCELLED;

	/** {@code true} when the scheduler owns the task and will run (or has run) it. */
	public boolean isAdmitted() {
		return this == ACCEPTED || this == QUEUED || this == COALESCED;
	}

	/** {@code true} when the submission was refused outright. */
	public boolean isRejection() {
		return this == REJECTED_CAPACITY || this == REJECTED_CLOSED || this == CANCELLED;
	}
}
