package io.github.hstefanov1.distributed.jobscheduler.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Registry of all supported job types in the distributed scheduler.
 * <p>
 * This enumeration maps distinct tasks to their unique execution processors
 * and provides stable and unique integer identifiers required for PostgreSQL advisory locking.
 * <p>
 * Adding a new job requires a new constant here, a new {@code JobProcessor} and a
 * matching {@code JobConfig} row.
 */
@AllArgsConstructor
public enum JobName {

    EXAMPLE_SLOW(1),
    EXAMPLE_FAST(2);

    /**
     * The stable, unique integer identifier for this job.
     * <p>
     * This ID is critical since it serves as a key for PostgreSQL advisory lock
     * preventing concurrent execution of the same job across scheduler replicas.
     */
    @Getter
    final int id;
}
