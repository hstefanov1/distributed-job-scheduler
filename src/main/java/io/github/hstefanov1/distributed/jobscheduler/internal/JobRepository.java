package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
class JobRepository {

  /**
   * Claims due jobs for this instance, following the rules:
   * <p>
   * <ul>
   *   <li>
   *     {@code enabled=true}
   *   </li>
   *   <li>
   *     {@code nextRunAt=<expired>}
   *   </li>
   *   <li>
   *      {@code ownerHeartbeatAt=<expired>}
   *   </li>
   * </ul>
   *
   * @param batchLimit maximum number of jobs to claim in this call
   * @return jobs needed to run
   */
  @Transactional
  public List<JobConfig> claimDueJobs(int batchLimit) {
    String sql = """
        UPDATE scheduler.job_config
        SET owner_id = :ownerId,
            owner_heartbeat_at = now(),
            version = version + 1
        WHERE id IN (
            SELECT id
            FROM scheduler.job_config
            WHERE enabled = true
              AND next_run_at <= now()
              AND owner_heartbeat_at <= now() - interval '1 minutes'
            ORDER BY next_run_at
            LIMIT :batchLimit
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id
        """;
    List<?> rows = JobConfig.getEntityManager()
        .createNativeQuery(sql)
        .setParameter("ownerId", JobIdentity.OWNER_ID)
        .setParameter("batchLimit", batchLimit)
        .getResultList();
    List<Long> jobIds = rows.stream().map(r -> ((Number) r).longValue()).toList();
    return JobConfig.findByIds(jobIds);
  }

  /**
   * Marks the job as completed and schedules its next eligible run.
   * <p>
   * Releases ownership ({@code ownerId=NULL}) as it is no longer working on it.
   *
   * @param jobId id of the job that finished running
   */
  @Transactional
  public void completeJob(long jobId) {
    JobConfig job = JobConfig.<JobConfig>find("id = ?1 and ownerId = ?2",
        jobId, JobIdentity.OWNER_ID).firstResult();

    // warn user about unexpected behavior
    if (job == null) {
      String message = "Could not complete job [{}] with owner [{}] because it was not found "
          + "(another instance may be processing it). This is unsafe and unexpected behavior."
          + " Consider time to investigate it.";
      log.warn(message, jobId, JobIdentity.OWNER_ID);
      return;
    }

    // complete job
    JobName name = job.jobName;
    Instant now = Instant.now();
    job.lastRunAt = now;
    job.nextRunAt = now.plusSeconds(job.intervalSeconds);
    job.ownerId = null; // no longer working on it
    job.persist();

    if (log.isDebugEnabled()) {
      Instant nextRunAt = job.nextRunAt.truncatedTo(ChronoUnit.SECONDS);
      log.debug("Job [{}] rescheduled to [{}]", name, nextRunAt);
    }
  }
}
