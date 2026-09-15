# Distributed Job Scheduler

A lightweight, Postgres-backed job scheduler for Quarkus applications that run with multiple
replicas. It guarantees exactly-one-owner execution of each scheduled job across all replicas.

No external coordination service (Zookeeper, Redis, etcd) required.

## How it works

Every replica ticks on the same schedule (e.g., every 30s) and independently checks the job registry
for jobs that are enabled and due to run. Before executing, each replica attempts to acquire a
PostgreSQL session-level advisory lock via `pg_try_advisory_lock`. Only the replica that
successfully
acquires the lock runs the job. All others skip the tick and wait for the next one.

If the owning replica crashes or shuts down, its database connection is terminated and the
session-level lock is automatically released by Postgres (no manual cleanup or heartbeat/TTL logic
needed). The next tick from any surviving replica will then acquire the lock and take over
ownership.

## Advisory lock configuration

Based on internal evaluation (see [docs/tech-studies](docs/tech-studies/README.md)), the scheduler uses:

| Aspect           | Choice                                                        | Why                                                                                                        |
|------------------|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| **Lock type**    | Session-level (`pg_try_advisory_lock` / `pg_advisory_unlock`) | Auto-releases on disconnect, so crash recovery is handled by Postgres itself rather than custom TTL logic. |
| **Lock holder**  | Exclusive                                                     | Only one replica may run a given job at a time                                                             |
| **Acquire mode** | Non-blocking (`pg_try_advisory_lock`)                         | Replicas that don't win the lock return immediately and skip the tick, instead of piling up waiting.       |

Advisory locks are process-wide per database (they don't cross databases), so all replicas must
point at the same database for leader election to work correctly. Lock keys are kept intentionally
scoped to avoid exhausting shared memory from too many distinct keys.

### Job execution strategy: batched fetch + entity-managed update loop

For processing expired/inactive entries (e.g., deactivating memberships), the scheduler deliberately
avoids bulk SQL/JPQL updates in favor of a paginated fetch + entity-managed update loop:

```java
Membership.find("status = ACTIVE and endDate <= ?1", Instant.now())
    .page(0, 500)
    .list().forEach(m -> m.status = INACTIVE);
```

This was chosen over raw bulk updates for three reasons:

1. **Optimistic locking is preserved:** entities stay managed by Hibernate, so @Version increments
   naturally and concurrent edits raise a version-mismatch exception instead of being silently
   overwritten.
2. **Lifecycle hooks fire correctly:** @PreUpdate runs as expected (e.g., updatedAt stays accurate)
   without needing to duplicate that logic manually into SQL/JPQL.
3. **Reduced lock contention:** batching in pages of 500 holds row locks on a much smaller working
   set per transaction, rather than locking the entire matching set at once, which reduces blocking
   against concurrent user-facing updates.

**The trade-off is throughput:** this approach is slower and more memory-intensive than a single
bulk UPDATE, since it loads full entities into memory rather than materializing nothing. For this
scheduler's use case, correctness and safe concurrent handling were prioritized over raw speed.

## Core flow

1. **Job Registry:** fetches `enabled` jobs whose `next_run_at` has passed.
2. **Job Executor:** attempts to acquire the advisory lock, skips the tick if the job is already
   running or the lock is held elsewhere.
3. **Job Processor:** executes the job's business logic via the batched fetch + entity-managed
   update loop, emitting metrics to Prometheus along the way.
4. **Job Reschedule:** persists `last_run_at` and computes the next `next_run_at` based on the
   configured interval.

![Job scheduler flow diagram](docs/images/workflow-diagram.png)

## Requirements

- Java 21 (GraalVM)
- Quarkus 3.39.3
- Hibernate ORM with Panache
- PostgreSQL Database (Docker/Podman)
- Apache Maven

## Run in dev mode

```shell script
./mvnw quarkus:dev
```

**Note:** quarkus dev services will automatically start a PostgreSQL container for you (via
Testcontainers). No manual setup needed.

Current datasource configuration:

- **Host/Port:** `localhost:5432`
- **Database:** `quarkus`
- **Username:** `quarkus`
- **Password:** `quarkus`
- **Schema:** `scheduler`
- **JDBC:** `jdbc:postgresql://localhost:5432/quarkus`

## Package and run

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application is now runnable using `java -jar target/*-runner.jar`.

## Create native executable

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container
using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with:

```shell script
./target/distributed-job-scheduler-*-runner
```
