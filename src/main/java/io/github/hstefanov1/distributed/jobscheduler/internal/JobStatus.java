package io.github.hstefanov1.distributed.jobscheduler.internal;

/**
 * Represents the outcome status of a scheduled job run.
 */
enum JobStatus {

    /**
     * The initial/default state of a job configuration before it has ever run.
     */
    PENDING,

    /**
     * Indicates that the last execution of the job completed successfully
     * without throwing any unhandled exceptions.
     */
    COMPLETED,

    /**
     * Indicates that the last execution of the job failed due to an unhandled
     * exception or error during processing.
     */
    FAILED
}
