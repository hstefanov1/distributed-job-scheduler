package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Runtime context passed to a {@link JobProcessor} when a job executes.
 *
 * <p>Carries the information a processor needs to run a single execution,
 * without exposing scheduling or persistence internals.
 *
 * @param jobName   the identity of the job being executed
 * @param batchSize the maximum number of items the processor should handle in this run
 */
public record JobContext(
    JobName jobName,
    int batchSize
) {

}
