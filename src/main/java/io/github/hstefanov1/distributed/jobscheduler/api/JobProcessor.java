package io.github.hstefanov1.distributed.jobscheduler.api;

/**
 * Contract for a unit of work that can be scheduled and executed by the system.
 * <p>
 * Implementations are discovered automatically and dispatched by {@code jobName()} when their
 * corresponding {@code JobConfig} becomes due.
 * <p>
 * Each processor should be stateless and safe to invoke repeatedly on a schedule.
 */
public interface JobProcessor {

  /**
   * The job this processor handles.
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
