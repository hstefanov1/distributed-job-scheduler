package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Runtime context passed to a {@link JobProcessor} when a job executes.
 * <p>
 * Carries the information a processor needs to run a single execution, without exposing scheduler internals.
 *
 * @param jobName   the job name
 * @param ownerId   the owner identifier (instance name) for this job
 * @param batchSize the number of records to be processed per batch for this job
 */
public record JobContext(
        JobName jobName,
        String ownerId,
        int batchSize
) {

}
