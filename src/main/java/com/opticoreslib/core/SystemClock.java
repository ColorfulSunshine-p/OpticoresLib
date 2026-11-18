package com.opticoreslib.core;

import java.time.Instant;

enum SystemClock implements Clock {

	INSTANCE;

	@Override
	public long nanoTime() {
		return System.nanoTime();
	}

	@Override
	public Instant instant() {
		return Instant.now();
	}
}
