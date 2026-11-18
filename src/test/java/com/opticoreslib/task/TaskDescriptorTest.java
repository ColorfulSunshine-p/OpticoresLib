package com.opticoreslib.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TaskDescriptorTest {

	@Test
	void appliesDefaults() {
		TaskDescriptor descriptor = TaskDescriptor.builder(Task.noop()).build();

		assertNotNull(descriptor.id());
		assertEquals(TaskGroup.DEFAULT_GROUP, descriptor.groupId());
		assertEquals(TaskPriority.NORMAL, descriptor.priority());
		assertSameNoneToken(descriptor);
		assertNull(descriptor.coalescingKey());
		assertNull(descriptor.completion());
		assertTrue(descriptor.attributes().isEmpty());
	}

	@Test
	void rejectsMissingTask() {
		assertThrows(IllegalStateException.class, () -> TaskDescriptor.builder().build());
	}

	@Test
	void keepsConsumerAttributes() {
		TaskDescriptor descriptor = TaskDescriptor.builder(Task.noop())
				.attribute("chunk", "0,0")
				.attribute("radius", 4)
				.build();

		Map<String, Object> attributes = descriptor.attributes();
		assertEquals(2, attributes.size());
		assertEquals("0,0", attributes.get("chunk"));
		assertEquals(4, attributes.get("radius"));
	}

	@Test
	void attributesAreImmutable() {
		TaskDescriptor descriptor = TaskDescriptor.builder(Task.noop()).attribute("a", 1).build();
		assertThrows(UnsupportedOperationException.class, () -> descriptor.attributes().put("b", 2));
	}

	@Test
	void withTaskKeepsMetadata() {
		TaskDescriptor original = TaskDescriptor.builder(Task.valueOf(ctx -> "first"))
				.id("task-1")
				.group("chunks")
				.priority(TaskPriority.HIGH)
				.coalescingKey("chunk-1")
				.attribute("a", 1)
				.build();

		TaskDescriptor copy = original.withTask(Task.valueOf(ctx -> "second"));

		assertEquals("task-1", copy.id());
		assertEquals("chunks", copy.groupId());
		assertEquals(TaskPriority.HIGH, copy.priority());
		assertEquals("chunk-1", copy.coalescingKey());
		assertEquals(1, copy.attributes().get("a"));
	}

	@Test
	void taskGroupsRejectUnboundedConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> new TaskGroup("g", 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new TaskGroup("g", 1, 0));
		assertThrows(IllegalArgumentException.class, () -> new TaskGroup(" ", 1, 1));
	}

	@Test
	void taskGroupCancelsAll() {
		TaskGroup group = TaskGroup.of("chunks");
		assertFalse(group.isCancelled());
		group.cancelAll();
		assertTrue(group.isCancelled());
		assertTrue(group.cancellationToken().isCancelled());
	}

	private static void assertSameNoneToken(TaskDescriptor descriptor) {
		assertTrue(descriptor.cancellationToken() == CancellationToken.none()
				|| !descriptor.cancellationToken().isCancelled());
	}
}
