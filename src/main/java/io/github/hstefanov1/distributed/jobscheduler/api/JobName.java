package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Identifies each job known to the scheduling system.
 *
 * <p>Each enum corresponds to a {@code job_name} value in the
 * {@code JobConfig} table.
 * <p>
 * Adding a new job requires both a new constant here and a matching {@code JobConfig} row.
 */
public enum JobName {

  /**
   * Deactivates records that have passed their expiration date.
   */
  DEACTIVATE_EXPIRED
}
