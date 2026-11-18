package com.opticoreslib.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CancellationTest {

	@Test
	void sourceSignalsCancellationOnce() {
		CancellationSource source = CancellationSource.create();
		assertFalse(source.isCancelled());
		assertFalse(source.token().isCancelled());

		source.cancel();
		source.cancel();

		assertTrue(source.isCancelled());
		assertTrue(source.token().isCancelled());
	}

	@Test
	void listenersRunOnCancellation() {
		CancellationSource source = CancellationSource.create();
		AtomicInteger calls = new AtomicInteger();
		source.token().onCancel(calls::incrementAndGet);

		assertEquals(0, calls.get());
		source.cancel();
		assertEquals(1, calls.get());
	}

	@Test
	void listenerRegisteredAfterCancellationRunsImmediately() {
		CancellationSource source = CancellationSource.create();
		source.cancel();

		AtomicInteger calls = new AtomicInteger();
		source.token().onCancel(calls::incrementAndGet);

		assertEquals(1, calls.get());
	}

	@Test
	void disposingARegistrationRemovesTheListener() {
		CancellationSource source = CancellationSource.create();
		AtomicInteger calls = new AtomicInteger();
		CancellationRegistration registration = source.token().onCancel(calls::incrementAndGet);

		registration.dispose();
		source.cancel();

		assertEquals(0, calls.get());
	}

	@Test
	void scopeCancelsItsChildren() {
		CancellationScope scope = CancellationScope.named("world");
		CancellationSource child = scope.register(CancellationSource.create());

		assertFalse(scope.isCancelled());
		assertFalse(child.isCancelled());

		scope.cancel();

		assertTrue(scope.isCancelled());
		assertTrue(child.isCancelled());
		assertTrue(scope.token().isCancelled());
	}

	@Test
	void childRegisteredAfterScopeCancellationIsCancelledImmediately() {
		CancellationScope scope = CancellationScope.named("world");
		scope.cancel();

		CancellationSource child = scope.register(CancellationSource.create());

		assertTrue(child.isCancelled());
	}

	@Test
	void noneTokenNeverCancels() {
		assertFalse(CancellationToken.none().isCancelled());
		CancellationRegistration registration = CancellationToken.none().onCancel(() -> {
			throw new AssertionError("must never run");
		});
		registration.dispose();
	}

	@Test
	void sourceTokenIdentityIsStable() {
		CancellationSource source = CancellationSource.create();
		assertSame(source.token(), source.token());
	}
}
