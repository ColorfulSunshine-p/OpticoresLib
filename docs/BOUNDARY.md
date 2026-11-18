# Library boundary and dependency direction

This document is the Phase 0 deliverable of the shared infrastructure plan. It records
which code is generic infrastructure, which code is a Minecraft binding and which code
belongs to the consumer, so that the boundary does not erode over time.

## Dependency direction

```
        +---------------------------+
        |          OptiCores        |   gameplay policy, chunk priorities,
        |  (consumer, not shipped    |   GUI, DH/Voxy/Chunky adapters
        |       by this library)     |
        +-------------+-------------+
                      |
                      |  depends on (one direction only)
                      v
        +---------------------------+
        |        OptiCoresLib       |   reusable primitives and contracts
        |  com.opticoreslib.*       |   no gameplay policy
        +---------------------------+

        OptiCoresLib never depends on OptiCores.
```

The rule is one-directional and machine-checked. `PackageBoundaryTest` inspects the
compiled classes and fails the build if any class in the library references
`com/opticores/`.

### The OptiCores dependency is "provided", not required

`build.gradle` declares OptiCores as a **compile only** mod dependency:

```gradle
modCompileOnly files("libs/Opticores-1.1.1+1.21.1.jar")
```

| Property | Value |
|---|---|
| Compile classpath | yes, the integration surface can be type checked against the real consumer |
| Runtime classpath | yes in the dev environment, so `runClient` exercises the real combination |
| Bundled into the published jar | **no** |
| Required at runtime | **no** |

`fabric.mod.json` expresses the same relationship as a soft hint rather than a hard
dependency:

```json
"suggests": { "opticores": "*" }
```

Consequences that the test suite enforces:

- The published artifact contains no `com/example/opticore/**` class.
- No class in the version neutral core references an OptiCores type.
- The library still starts and works when OptiCores is not installed.


## Module classification

The plan lists eight logical modules. In this repository they are packages inside a single
artifact, which keeps the consumer's dependency list to one entry and avoids publishing
duplicate classes.

| Module | Package | Version neutral | Contains | Must not contain |
|---|---|---|---|---|
| `core-api` | `com.opticoreslib.core` | yes | `Clock`, `ManagedService`, `ServiceRegistry`, `ServiceState`, `Diagnostics`, `FeatureState`, `Fallback`, `CachedProbe`, `Ids`, `sync.FrameCounter`, `sync.FrameFence` | Minecraft types, OptiCores types |
| `task` | `com.opticoreslib.task` | yes | `Task`, `TaskContext`, `TaskDescriptor`, `TaskGroup`, `TaskPriority`, `TaskResult`, `SubmissionResult`, `SubmissionStatus`, `CancellationToken`, `CancellationSource`, `CancellationScope`, `CompletionDispatcher` | Minecraft types, scheduling mechanics |
| `scheduler` | `com.opticoreslib.scheduler` | yes | `TaskScheduler`, `DefaultTaskScheduler`, `SchedulerOptions`, `SchedulerStats`, `AgingPolicy`, `BackpressureSignal` | chunk access, priority policy for a specific consumer |
| `concurrency` | `com.opticoreslib.concurrent` | yes | `ManagedExecutor`, `BoundedManagedExecutor`, `ManagedExecutorConfig`, `RejectionStrategy`, `WorkerStats`, `WorkloadProfile`, `NamedThreadFactory` | a global executor, automatic pool creation |
| `resources` | `com.opticoreslib.resources` | yes | `ResourceSnapshot`, `GraphicsDeviceSnapshot`, `Measurement`, `Confidence`, `ResourceProbe`, `GraphicsDeviceProbe`, `SystemResourceProbe`, `UnknownGraphicsDeviceProbe` | hard-coded VRAM assumptions, renderer access |
| `metrics` | `com.opticoreslib.metrics` | yes | `Counter`, `Gauge`, `Timer`, `Histogram`, `RollingHistogram`, `Percentiles`, `MetricRegistry`, `MetricsSnapshot`, `MetricSnapshot`, `FrameBudget` | network telemetry, unbounded storage |
| `compat` | `com.opticoreslib.integration` | yes | `IntegrationProvider`, `IntegrationRegistry`, `IntegrationContext`, `IntegrationState`, `CapabilityKey` | a direct dependency on DH/Voxy/Chunky/Sodium |
| `testing` | `src/testFixtures/java/com/opticoreslib.testing` | yes | `FakeClock`, `ManualExecutor`, `TestSupport` | runtime production hooks |
| `platform-fabric` | `com.opticoreslib.fabric` | **no** | `OpticoresLib`, `OpticoresLibClient`, `LibraryRuntime`, `client.LwjglGraphicsDeviceProbe` | business logic, gameplay policy |

`testing` lives in a separate Gradle source set, so the test doubles are never packaged
into the mod jar and can never be reached at runtime.

## What stays out of OptiCoresLib

| Concern | Owner | Reason |
|---|---|---|
| Chunk priority and player look-ahead | OptiCores | Minecraft gameplay semantics, not generic infrastructure |
| Chunk lifecycle mixins and world access | OptiCores | version sensitive Minecraft integration |
| Distant Horizons / Voxy / Chunky adapters | OptiCores | mod specific compatibility and release cadence |
| ACPS GUI and user-facing presets | OptiCores | product specific settings and diagnostics |
| GPU mesh pipeline and render hooks | OptiCores | renderer specific implementation and feature ownership |
| Automatic chunk generation decisions | OptiCores | must remain under the consumer's explicit policy |
| Backpressure policy (what "too much load" means) | OptiCores | the library exposes the signal, the consumer decides the threshold |

## What was removed from the previous template

The repository previously contained prototype classes that violated the boundary. They were
audied and either migrated, moved out or dropped.

| Previous class | Decision | Reason |
|---|---|---|
| `com.opticores.lib.api.FeatureState` | migrated to `com.opticoreslib.core.FeatureState` | generic capability contract |
| `com.opticores.lib.api.Fallback` | migrated to `com.opticoreslib.core.Fallback` | generic fallback helper |
| `com.opticores.lib.diagnostics.Diagnostics` | rewritten as `com.opticoreslib.core.Diagnostics` | the old version was a static SLF4J helper; the new one has a pluggable sink so the core stays dependency free |
| `com.opticores.lib.util.Timer` | replaced by `com.opticoreslib.metrics.Timer` | merged into the metrics module |
| `com.opticores.lib.util.FrameBudget` | rewritten as `com.opticoreslib.metrics.FrameBudget` | the old EMA update was mathematically wrong and used a `DoubleAdder` as a scalar |
| `com.opticores.lib.sync.FrameId` | replaced by `com.opticoreslib.core.sync.FrameCounter` | the old class used a global mutable static |
| `com.opticores.lib.sync.FrameFence` | rewritten as `com.opticoreslib.core.sync.FrameFence` | the old class busy-waited with `Thread.onSpinWait()` and offered an unbounded wait |
| `com.opticores.lib.gpu.GPUResource` | **removed** | GPU resource pooling is renderer specific and belongs to the consumer |
| `com.opticores.lib.client.gpu.*` | **removed** | shader pack detection is a consumer concern; only the generic OpenGL probe stayed, and it moved to `com.opticoreslib.fabric.client` |
| `opticoreslib.mixins.json` / `opticoreslib.client.mixins.json` | **removed** | the library ships no mixins; the main config also referenced a class that did not exist |
| `OpticoresLibDataGenerator` | **removed** | the library generates no data |

## Version targeting

This repository targets **one** Minecraft patch release. The values are pinned in
`gradle.properties`:

| Key | Value |
|---|---|
| `minecraft_version` | `1.21.1` |
| `yarn_mappings` | `1.21.1+build.3` |
| `loader_version` | `0.19.5` |
| `loom_version` | `1.16.1` |
| `fabric_api_version` | `0.116.17+1.21.1` |
| Java toolchain | `21` (declared in `build.gradle`) |

To add a target, fork the repository into a version specific branch or directory, change
the pinned values, re-verify every Fabric and Minecraft API used, and publish a separate
artifact. Do not copy mixin targets or internal method signatures between versions without
verifying them against the target source and bytecode.

## How the boundary is enforced

| Rule | Enforced by |
|---|---|
| The core never references Minecraft, Fabric or LWJGL | `PackageBoundaryTest.corePackagesDoNotReferenceMinecraftFabricOrOptiCores` |
| The library never references OptiCores | `PackageBoundaryTest.noPackageReferencesOptiCoresItself` |
| The library ships no mixins | `PackageBoundaryTest.theLibraryDoesNotShipMixins` |
| The library depends on no optional mod | `PackageBoundaryTest.theLibraryDoesNotDependOnOptionalMods` |
| The library is reusable without OptiCores | `IndependentConsumerTest` |
| Queues and pools are bounded | `BoundedManagedExecutorTest`, `DefaultTaskSchedulerTest` |
| Unknown measurements stay unknown | `MeasurementTest`, `SystemResourceProbeTest` |
| Shutdown leaves no threads behind | `DefaultTaskSchedulerTest.leavesNoWorkerThreadsBehind`, `BoundedManagedExecutorTest.closesWithoutLeavingThreadsBehind` |
