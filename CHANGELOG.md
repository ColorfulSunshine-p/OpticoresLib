# Changelog

All notable changes to OptiCoresLib are documented here.

The library follows semantic versioning for the public API. A breaking change to a public
type is a major version bump and is called out explicitly below.

## [v1.2+1.21.x-fabric] - 2026-11-15

Target: Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Java 21.

### Added

- OptiCores is now declared as a **provided** (compile only) mod dependency:
  `modCompileOnly files("libs/Opticores-1.1.1+1.21.1.jar")`. It is on the
  compile classpath for type checking the integration surface, but it is not bundled into
  the artifact and not required at runtime. `fabric.mod.json` expresses the same
  relationship as `"suggests": { "opticores": "*" }`.
- `core-api`: `Clock`, `ManagedService`, `ServiceRegistry`, `ServiceState`, `Diagnostics`,
  `FeatureState`, `Fallback`, `CachedProbe`, `Ids`, `sync.FrameCounter`, `sync.FrameFence`.
- `task`: `Task`, `TaskContext`, `TaskDescriptor`, `TaskGroup`, `TaskPriority`,
  `TaskResult`, `SubmissionResult`, `SubmissionStatus`, `CancellationToken`,
  `CancellationSource`, `CancellationScope`, `CompletionDispatcher`.
- `scheduler`: `TaskScheduler`, `DefaultTaskScheduler`, `SchedulerOptions`,
  `SchedulerStats`, optional `AgingPolicy` and external `BackpressureSignal`.
- `concurrency`: `ManagedExecutor`, `BoundedManagedExecutor`, `ManagedExecutorConfig`,
  `RejectionStrategy`, `WorkerStats`, `WorkloadProfile`, `NamedThreadFactory`.
- `resources`: `ResourceSnapshot`, `GraphicsDeviceSnapshot`, `Measurement`, `Confidence`,
  `ResourceProbe`, `GraphicsDeviceProbe`, `SystemResourceProbe`,
  `UnknownGraphicsDeviceProbe`.
- `metrics`: `Counter`, `Gauge`, `Timer`, `Histogram`, `RollingHistogram`, `Percentiles`,
  `MetricRegistry`, `MetricsSnapshot`, `MetricSnapshot`, `FrameBudget`.
- `compat`: `IntegrationProvider`, `IntegrationRegistry`, `IntegrationContext`,
  `IntegrationState`, `CapabilityKey`.
- `testing`: a separate Gradle source set with `FakeClock`, `ManualExecutor` and
  `TestSupport`. It is never packaged into the mod jar.
- `platform-fabric`: `OpticoresLib` and `OpticoresLibClient` entrypoints, `LibraryRuntime`
  and an OpenGL based `GraphicsDeviceProbe`.
- `docs/BOUNDARY.md` and `docs/API.md`.

### Changed

- The package root moved from `com.opticores.lib` to `com.opticoreslib`.
- `Diagnostics` is now instance based with a pluggable sink, so the version neutral core
  does not depend on a logging framework.
- `FrameBudget` uses a correct exponential moving average and a monotonic clock.
- `FrameFence` waits on a condition variable instead of spinning, and every wait takes a
  timeout.
- The frame counter is an instance instead of a global mutable static.

### Removed

- `com.opticores.lib.gpu.GPUResource` and the client GPU helpers. GPU resource pooling and
  shader pack detection are consumer concerns.
- The template mixin configurations and `ExampleMixin` / `ExampleClientMixin`. The library
  ships no mixins.
- `OpticoresLibDataGenerator`. The library generates no data.

### Fixed

- The main mixin configuration referenced a class that did not exist and would have failed
  at load time.
- `fabric.mod.json` declared no entrypoints, so the initializers were never called.
- The build used a machine specific `org.gradle.java.home` that would have broken every
  non-Windows CI run.
- The client shutdown hook only logged that it was releasing the infrastructure without
  actually releasing it. `ClientLifecycleEvents.CLIENT_STOPPING` now closes the runtime.
- The `SERVER_STOPPED` hook is only registered on a dedicated server. On a client the
  integrated server stops whenever the player leaves a world, which must not tear down the
  client side runtime.
- The mod version is now pinned in one place (`mod_version` in `gradle.properties`),
  previously `build.gradle` and `gradle.properties` disagreed on it.

### Verified

- `gradlew build` and `gradlew runClient` on the pinned target.
- The client reaches the main menu with the library installed, both entrypoints run, and
  closing the window releases the runtime with no exception and no leftover worker thread.
