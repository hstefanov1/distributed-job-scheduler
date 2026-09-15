package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Contract for a unit of work that can be scheduled and executed by the system.
 * <p>
 * The system automatically discovers implementations and dispatches them via {@code jobName()} when their
 * corresponding {@code JobConfig} becomes due.
 * <p>
 * Each processor should be stateless and safe to invoke repeatedly on a schedule.
 */
public interface JobProcessor {

    /**
     * The job name this processor handles.
     *
     * @return the {@link JobName} this implementation is registered under
     */
    JobName name();

    /**
     * Executes one run of this job.
     *
     * @param context runtime information for this execution, such as batch size
     */
    void process(JobContext context);
}
