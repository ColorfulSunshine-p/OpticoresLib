package com.opticoreslib.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.opticoreslib.task.TaskPriority;

class AgingPolicyTest {

	@Test
	void noneNeverChangesThePriority() {
		AgingPolicy policy = AgingPolicy.none();
		assertEquals(TaskPriority.LOW, policy.effectivePriority(TaskPriority.LOW, Duration.ofHours(1)));
		assertEquals(TaskPriority.CRITICAL, policy.effectivePriority(TaskPriority.CRITICAL, Duration.ZERO));
	}

	@Test
	void promotesOneLevelPerThreshold() {
		AgingPolicy policy = AgingPolicy.byThreshold(TaskPriority.CRITICAL, Duration.ofSeconds(1));

		assertEquals(TaskPriority.LOW, policy.effectivePriority(TaskPriority.LOW, Duration.ofMillis(500)));
		assertEquals(TaskPriority.NORMAL, policy.effectivePriority(TaskPriority.LOW, Duration.ofSeconds(1)));
		assertEquals(TaskPriority.HIGH, policy.effectivePriority(TaskPriority.LOW, Duration.ofSeconds(2)));
		assertEquals(TaskPriority.CRITICAL, policy.effectivePriority(TaskPriority.LOW, Duration.ofSeconds(3)));
	}

	@Test
	void neverPromotesBeyondTheCeiling() {
		AgingPolicy policy = AgingPolicy.byThreshold(TaskPriority.NORMAL, Duration.ofSeconds(1));
		assertEquals(TaskPriority.NORMAL, policy.effectivePriority(TaskPriority.LOW, Duration.ofHours(10)));
	}

	@Test
	void keepsTheDeclaredPriorityBelowTheThreshold() {
		AgingPolicy policy = AgingPolicy.byThreshold(TaskPriority.CRITICAL, Duration.ofSeconds(5));
		assertEquals(TaskPriority.LOW, policy.effectivePriority(TaskPriority.LOW, Duration.ofSeconds(4)));
		assertEquals(TaskPriority.NORMAL, policy.effectivePriority(TaskPriority.LOW, Duration.ofSeconds(5)));
	}

	@Test
	void rejectsInvalidConfiguration() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> AgingPolicy.byThreshold(TaskPriority.CRITICAL, Duration.ZERO));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> AgingPolicy.byThreshold(null, Duration.ofSeconds(1)));
		assertTrue(true);
	}
}
