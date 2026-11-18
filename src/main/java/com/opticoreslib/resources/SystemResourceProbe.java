package com.opticoreslib.resources;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;

/**
 * Default {@link ResourceProbe} backed by the JVM and the operating system.
 *
 * <p>The probe never fails. Anything the platform does not expose is reported as
 * {@link Measurement#unknown(String)}, which keeps the contract "unknown stays unknown"
 * intact on every operating system.
 */
public final class SystemResourceProbe implements ResourceProbe {

	private static final String SOURCE = "jvm+os";
	private static final String UNKNOWN_SOURCE = "unsupported-by-platform";

	private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
	private final Runtime runtime = Runtime.getRuntime();

	@Override
	public ResourceSnapshot probe() {
		Instant now = Instant.now();
		return new ResourceSnapshot(
				now,
				Measurement.measured(osBean.getAvailableProcessors(), SOURCE),
				Measurement.measured(runtime.availableProcessors(), SOURCE),
				totalPhysicalMemory(),
				freePhysicalMemory(),
				Measurement.measured(runtime.totalMemory(), SOURCE),
				Measurement.measured(runtime.totalMemory() - runtime.freeMemory(), SOURCE),
				Measurement.measured(runtime.maxMemory(), SOURCE),
				systemLoadAverage());
	}

	@Override
	public String source() {
		return SOURCE;
	}

	private Measurement<Long> totalPhysicalMemory() {
		Long value = invokeLong("getTotalPhysicalMemorySize");
		return value == null ? Measurement.unknown(UNKNOWN_SOURCE) : Measurement.measured(value, SOURCE);
	}

	private Measurement<Long> freePhysicalMemory() {
		Long value = invokeLong("getFreePhysicalMemorySize");
		return value == null ? Measurement.unknown(UNKNOWN_SOURCE) : Measurement.measured(value, SOURCE);
	}

	private Measurement<Double> systemLoadAverage() {
		try {
			double value = osBean.getSystemLoadAverage();
			// A negative load average means the platform does not provide one.
			return value < 0.0 ? Measurement.unknown(UNKNOWN_SOURCE) : Measurement.measured(value, SOURCE);
		} catch (RuntimeException e) {
			return Measurement.unknown(UNKNOWN_SOURCE);
		}
	}

	/**
	 * Invokes an optional method of {@code com.sun.management.OperatingSystemMXBean}.
	 *
	 * <p>The interface is looked up reflectively so that the library keeps working on JVMs
	 * that do not provide it, and so that no non-portable API is referenced at compile time.
	 */
	private Long invokeLong(String methodName) {
		try {
			Class<?> extended = Class.forName("com.sun.management.OperatingSystemMXBean");
			if (!extended.isInstance(osBean)) {
				return null;
			}
			Object value = extended.getMethod(methodName).invoke(osBean);
			return value instanceof Number number ? number.longValue() : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
