package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Identifies each job known to the scheduling system.
 * <p>
 * Each enum corresponds to a {@code jobName} value in the {@code JobConfig} table.
 * <p>
 * Adding a new job requires a new constant here, a new {@code JobProcessor} and a
 * matching {@code JobConfig} row.
 */
public enum JobName {

    /**
     * Deactivates records that have passed their expiration date.
     */
    DEACTIVATE_EXPIRED
}
