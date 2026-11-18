package com.opticoreslib.task;

import java.time.Duration;

/**
 * Outcome of a task that has been executed.
 *
 * <p>Every task produces exactly one of these, including tasks that failed or were
 * cancelled. Failures are values, never thrown exceptions, so that a consumer can always
 * observe what happened to the work it submitted.
 */
public sealed interface TaskResult permits TaskResult.Success, TaskResult.Failure, TaskResult.Cancelled {

	/** Identifier of the task this result belongs to. */
	String taskId();

	/** How long the task ran for. */
	Duration duration();

	/** {@code true} only for {@link Success}. */
	boolean isSuccess();

	/** The task completed and returned a payload. */
	record Success(String taskId, Duration duration, Object value) implements TaskResult {

		@Override
		public boolean isSuccess() {
			return true;
		}
	}

	/** The task threw. */
	record Failure(String taskId, Duration duration, String errorType, String message, Throwable cause)
			implements TaskResult {

		public Failure {
			if (errorType == null) {
				errorType = "java.lang.Throwable";
			}
			if (message == null) {
				message = "";
			}
			if (duration == null) {
				duration = Duration.ZERO;
			}
		}

		@Override
		public boolean isSuccess() {
			return false;
		}
	}

	/** The task was cancelled before or during execution. */
	record Cancelled(String taskId, Duration duration) implements TaskResult {

		public Cancelled {
			if (duration == null) {
				duration = Duration.ZERO;
			}
		}

		@Override
		public boolean isSuccess() {
			return false;
		}
	}
}
