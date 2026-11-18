# OptiCoresLib

Shared, version-specific processing infrastructure for OptiCores and for any other mod
that needs the same primitives: bounded task scheduling, managed worker pools, resource
capability snapshots, local metrics and optional capability discovery.

OptiCoresLib contains **no gameplay policy**. It does not know what a chunk is, it does not
decide what to generate and it does not integrate with Distant Horizons, Voxy, Chunky,
Sodium or Iris. It provides the reusable, bounded, observable primitives that a consumer
builds its own policy on top of.

- Minecraft target: **1.21.1** (pinned, see `gradle.properties`)
- Loader: Fabric Loader `0.19.5`
- Java: **21**
- Coordinates: `com.opticores.lib:opticoreslib:v1.2+1.21.x-fabric`

> [!IMPORTANT]
> 1.21.1 is a dedicated target. 1.21.10, 1.21.11 and the 26.x line need their own build
> target, their own pinned dependencies and their own verification of mixin targets and
> internal signatures. There is no cross-version binary compatibility promise.

## Installation

Add the library as a normal Fabric dependency of your mod:

```gradle
repositories {
    maven { url = 'https://maven.anibalmolina.dev/' } // or your local repository
}

dependencies {
    modImplementation "com.opticores.lib:opticoreslib:v1.2+1.21.x-fabric"
}
```

The library requires Fabric API (`>=0.116.17+1.21.1`) and nothing else. It does not
require any optional mod.

OptiCores is declared as a **provided** (compile only) dependency: it is on the compile
classpath so the integration surface can be type checked against the real consumer, but it
is not bundled into the artifact and it is not a runtime requirement. The library works
with or without OptiCores installed.

```gradle
modCompileOnly files("libs/Opticores-1.1.1+1.21.1.jar")
```

## Quick start

```java
import com.opticoreslib.fabric.OpticoresLib;
import com.opticoreslib.scheduler.TaskScheduler;
import com.opticoreslib.task.SubmissionResult;
import com.opticoreslib.task.TaskDescriptor;

TaskScheduler scheduler = OpticoresLib.requireRuntime().scheduler();

scheduler.registerGroup(TaskGroup.of("chunks", /* maxQueued */ 256, /* maxConcurrent */ 4));

SubmissionResult result = scheduler.submit(TaskDescriptor
        .builder(context -> {
            // do the background work here
            return "done";
        })
        .group("chunks")
        .priority(TaskPriority.HIGH)
        .onComplete((context, taskResult) -> {
            // runs on the worker thread unless a dispatcher is configured
        })
        .build());

if (!result.isAccepted()) {
    logger.warn("submission refused: {} ({})", result.status(), result.reason());
}
```

## Module map

The architecture plan describes eight logical modules. They are implemented as packages
inside one artifact so that a consumer never has to juggle several jars.

| Plan module | Package | Contents |
|---|---|---|
| `core-api` | `com.opticoreslib.core` | clock, lifecycle, service registry, diagnostics, feature state |
| `scheduler` | `com.opticoreslib.scheduler` | bounded priority queue, admission control, aging, backpressure |
| `concurrency` | `com.opticoreslib.concurrent` | managed worker pools, thread factory, rejection strategies |
| `resources` | `com.opticoreslib.resources` | CPU/memory/device snapshots with confidence metadata |
| `metrics` | `com.opticoreslib.metrics` | counters, gauges, timers, bounded rolling histograms |
| `compat` | `com.opticoreslib.integration` | optional integration and capability registry |
| `testing` | `src/testFixtures/java` | fake clock, deterministic executor, test helpers |
| `platform-fabric` | `com.opticoreslib.fabric` | Fabric lifecycle bindings and the OpenGL probe |

## Guarantees

These are enforced by the test suite, not just documented:

- **Bounded everywhere.** Every queue and every worker pool has a finite, configurable
  bound. There is no unbounded collection and no thread-per-task anywhere.
- **Typed outcomes.** A submission never returns a bare boolean. It returns a
  `SubmissionResult` with `ACCEPTED`, `QUEUED`, `COALESCED`, `DEFERRED`,
  `REJECTED_CAPACITY`, `REJECTED_CLOSED` or `CANCELLED`.
- **Failures are values.** A task that throws produces a `TaskResult.Failure`; it never
  kills a worker thread.
- **Unknown stays unknown.** Resource measurements carry a confidence level. A value the
  platform does not expose is `Measurement.unknown(...)`, never `0`. Shared system memory
  is never reported as dedicated VRAM.
- **Deterministic shutdown.** `stopAccepting()` &rarr; `drain(timeout)` &rarr; `close()`,
  in that order, and no worker thread survives `close()`.
- **No hidden services.** The library starts a worker pool only when a consumer asks for
  the scheduler.
- **Version neutral core.** `com.opticoreslib.{core,task,scheduler,concurrent,metrics,resources,integration}`
  never reference `net.minecraft`, `net.fabricmc`, `org.lwjgl` or OptiCores. This is
  verified by `PackageBoundaryTest`, which inspects the compiled classes.
- **No mixins.** The library ships no mixin configuration at all.

## Threading contract

- Task bodies run on a scheduler worker thread.
- Completion callbacks run through a `CompletionDispatcher`. `direct()` runs them inline on
  the worker; `of(executor)` runs them on an executor you own, for example to hop back to
  the Minecraft main thread.
- Minecraft thread affinity is the consumer's responsibility. The library never touches
  the render thread or the server thread.
- Worker threads are daemon threads by default. The choice is explicit and configurable
  through `ManagedExecutorConfig`.

## Documentation

- [`docs/BOUNDARY.md`](docs/BOUNDARY.md) - what stays in the library, what stays in the
  consumer, and the dependency direction.
- [`docs/API.md`](docs/API.md) - API reference with examples and lifecycle semantics.
- [`CHANGELOG.md`](CHANGELOG.md) - release notes.

## Building

```bash
./gradlew build      # compile, test and package
./gradlew test       # tests only
./gradlew runClient  # launch a development client
```

The build requires JDK 21. The toolchain is declared in `build.gradle`; Gradle resolves it
from the installed JDKs or provisions it.

### Test layout

| Layer | Where | What it covers |
|---|---|---|
| Unit | `src/test/java/com/opticoreslib/{core,task,scheduler,concurrent,metrics,resources,integration}` | ordering, bounds, cancellation, unknown values, lifecycle |
| Boundary | `src/test/java/com/opticoreslib/boundary` | the core stays version neutral, the library ships no mixins and no optional mod dependencies |
| Reusability | `src/test/java/com/opticoreslib/sample` | a consumer that compiles against the library without knowing OptiCores |

## License

MIT. See [`LICENSE`](LICENSE).
