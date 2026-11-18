# API reference

Every public type lives under `com.opticoreslib`. This document describes the contracts
that a consumer relies on: lifecycle, threading, bounds and failure behaviour.

## Lifecycle

All long lived services implement `com.opticoreslib.core.ManagedService`:

```
NEW ──start()──▶ STARTING ──▶ RUNNING ──stopAccepting()──▶ STOPPING ──▶ STOPPED ──close()──▶ CLOSED
                                                          ▲
                                                          └── any state may go to FAILED
```

| Method | Contract |
|---|---|
| `start()` | Idempotent when already running. Throws `IllegalStateException` when the service cannot be restarted. |
| `stopAccepting()` | Never throws. After it returns, new work is refused with a typed rejection, not an exception. |
| `drain(Duration)` | Never blocks longer than the timeout. |
| `close()` | Idempotent. Releases every resource, including worker threads. |

`ServiceRegistry` applies the same order to a list of services: start in registration
order, stop, drain and close in reverse registration order.

## Task submission

```java
SubmissionResult result = scheduler.submit(descriptor);
```

`SubmissionResult.status()` is one of:

| Status | Meaning | Typical reaction |
|---|---|---|
| `ACCEPTED` | Admitted and eligible to run. | continue |
| `QUEUED` | Admitted, but held back because the group reached its concurrency limit. | continue |
| `COALESCED` | Merged into an equivalent task that is already queued; only the newest payload survives. | continue |
| `DEFERRED` | Not admitted, the backpressure signal is active. | retry later |
| `REJECTED_CAPACITY` | Not admitted, a queue bound was reached. | shed load or retry |
| `REJECTED_CLOSED` | Not admitted, the scheduler is shutting down. | stop submitting |
| `CANCELLED` | Not admitted, the task or its group was already cancelled. | drop |

`result.reason()` carries a human readable explanation for every refusal.

### Task metadata

`TaskDescriptor` is immutable and carries no Minecraft object:

```java
TaskDescriptor.builder(task)
        .id("chunk-12")                        // optional, generated when omitted
        .group("chunks")                       // optional, defaults to "default"
        .priority(TaskPriority.HIGH)           // optional, defaults to NORMAL
        .coalescingKey("chunk-12")             // optional, enables coalescing
        .cancellation(source)                  // optional
        .attribute("radius", 4)                // optional consumer data
        .onComplete((context, result) -> { })  // optional
        .build();
```

The coalescing key is scoped to the task group. When two queued tasks of the same group
share a key, only the newest payload runs. Coalescing never applies to a task that is
already executing.

### Results

A task always produces exactly one `TaskResult`:

| Type | Meaning |
|---|---|
| `TaskResult.Success` | The task returned; carries the payload and the duration. |
| `TaskResult.Failure` | The task threw; carries the error type, the message and the cause. |
| `TaskResult.Cancelled` | The task was cancelled before or during execution. |

Failures never propagate out of the worker thread and never stop the scheduler.

### Cancellation

```java
CancellationScope scope = CancellationScope.named("world-1");
CancellationSource source = scope.register(CancellationSource.create());

scheduler.submit(TaskDescriptor.builder(task).cancellation(source).build());

// invalidates every task of this world at once
scope.cancel();
```

`CancellationToken` is read only, `CancellationSource` is the controller and
`CancellationScope` groups sources so that a whole session can be invalidated in one call.
Listeners registered with `token.onCancel(...)` can be removed again through the returned
`CancellationRegistration`.

## Scheduling

```java
SchedulerOptions options = SchedulerOptions.builder()
        .queueCapacity(1024)          // bounded, required
        .workerThreads(4)             // bounded, required
        .defaultGroupMaxQueued(256)   // bound for implicitly created groups
        .daemonWorkers(true)
        .shutdownTimeout(Duration.ofSeconds(10))
        .agingPolicy(AgingPolicy.byThreshold(TaskPriority.CRITICAL, Duration.ofSeconds(2)))
        .backpressure(() -> frameBudget.isOverBudget())
        .build();

DefaultTaskScheduler scheduler = new DefaultTaskScheduler(options, clock, CompletionDispatcher.direct());
scheduler.start();
```

Ordering rules:

1. Higher priority first.
2. Inside one priority, oldest submission first.
3. Aging is optional. When enabled, a task is promoted one level per threshold of waiting,
   up to the ceiling. The ordering is refreshed at a bounded resolution
   (`agingResolution`), so the cost stays predictable.

Backpressure is an external signal. The library never decides on its own what "too much
load" means; a consumer that knows about frame time or memory pressure wires it in.

### Task groups

```java
TaskGroup group = TaskGroup.of("chunks", /* maxQueued */ 256, /* maxConcurrent */ 4);
scheduler.registerGroup(group);

scheduler.groupStats("chunks");   // Optional<TaskGroupStats>
group.cancelAll();                // cancels queued and running tasks of the group
```

`maxQueued` and `maxConcurrent` are always positive and finite. A group created with
`TaskGroup.of(id)` uses `Integer.MAX_VALUE` for `maxConcurrent`, which means the group's
own concurrency limit is disabled and the scheduler's worker count is the effective bound.

## Managed worker pools

```java
ManagedExecutorConfig config = ManagedExecutorConfig.builder("io-lane")
        .maxConcurrency(4)
        .queueCapacity(256)
        .rejectionStrategy(RejectionStrategy.CALLER_RUNS)
        .daemon(true)
        .build();

ManagedExecutor pool = new BoundedManagedExecutor(config);
```

Rejection strategies: `ABORT` (throws `RejectedExecutionException`), `CALLER_RUNS`,
`DROP` (counted as dropped, never silent) and `DISCARD_OLDEST`.

`WorkloadProfile` offers a conservative starting point (`CPU_BOUND`, `IO_BOUND`,
`COMPLETION`), but the pool size is always the consumer's decision. The library never
creates a pool on its own and never infers free capacity from the core count alone.

## Resources

```java
ResourceSnapshot snapshot = new SystemResourceProbe().probe();

snapshot.logicalProcessors();          // Measurement<Integer>
snapshot.systemMemoryTotalBytes();      // Measurement<Long>
snapshot.heapUsedBytes();               // Measurement<Long>
```

`Measurement` carries a value, a `Confidence` and a source:

| Confidence | Meaning |
|---|---|
| `MEASURED` | Read directly from the operating system or driver. |
| `ESTIMATED` | Derived from measured data. |
| `GUESSED` | A rough guess. |
| `UNKNOWN` | Not available. The value is absent, never zero. |

Graphics snapshots keep dedicated VRAM, shared memory and total memory apart. The OpenGL
probe reports dedicated memory as unknown, because OpenGL does not expose it and shared
system memory of an integrated GPU must never be presented as dedicated VRAM. Capability
flags are exposed separately from any numeric estimate.

Expensive probes should be wrapped in `CachedProbe`, which bounds how often they run and
exposes the age of the cached value.

## Metrics

```java
MetricRegistry metrics = new MetricRegistry("opticores");

metrics.counter("chunks_generated").increment();
metrics.timer("chunk_latency").record(Duration.ofMillis(12));
metrics.gauge("queue_depth", () -> scheduler.queueDepth());

MetricsSnapshot snapshot = metrics.snapshot();
```

Metric names are namespaced, so two mods can never collide. Snapshots are immutable and
never expose a live collection. Timers and histograms keep a fixed size ring buffer, so
the memory footprint of the metrics module is bounded.

Nothing in this module transmits data anywhere.

## Optional integrations

```java
OpticoresLib.requireRuntime().integrations().register(provider);
```

An `IntegrationProvider` declares an id, a supported version range and its capabilities.
The registry starts a provider only when the host version is inside the range. A provider
that fails is reported through `registry.detail(id)` and never prevents the other
providers from starting.

The library ships the contract only. Concrete adapters for specific mods belong to the
consumer, which keeps the library free of any hard dependency on a mod most users do not
have. Presence checks happen at the platform edge; optional classes are never loaded when
the dependency is absent.

## Threading summary

| What | Where it runs |
|---|---|
| Task body | a scheduler worker thread |
| Completion callback | wherever the `CompletionDispatcher` sends it |
| `submit(...)` | the calling thread, never blocked by the task |
| Metrics and snapshots | safe from any thread |
| Resource probes | the caller's thread; the OpenGL probe must run on the graphics thread |

The library never touches the Minecraft main thread, the render thread or the server
thread. Hopping back to a specific thread is the consumer's job, using
`CompletionDispatcher.of(executor)`.

## Known limitations

- **No binary compatibility checker in CI yet.** The public API is versioned
  semantically and every breaking change is called out in `CHANGELOG.md`, but an automated
  API diff between releases is not wired into the build yet.
- **Group concurrency limits are per scheduler instance.** Two schedulers over the same
  group id do not share a limit. Use one scheduler per lane.
- **The OpenGL probe must run on the graphics thread.** It is not called during
  initialisation; call it from a frame boundary and wrap it in a `CachedProbe`.
- **Cross-version binary compatibility is not promised.** Each Minecraft target needs its
  own artifact and its own verification.
- **Aging is approximate.** With aging enabled the ordering can be up to one
  `agingResolution` step stale. The trade-off keeps the refresh cost predictable.

