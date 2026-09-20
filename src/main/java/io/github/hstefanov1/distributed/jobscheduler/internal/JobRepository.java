package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static io.github.hstefanov1.distributed.jobscheduler.internal.JobConstants.*;

@Slf4j
@ApplicationScoped
@NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class JobRepository {

    /**
     * Claims jobs that are enabled and due to run.
     *
     * @return jobs needed to run (max. of {@value JobConstants#MAX_CONCURRENT_JOBS})
     */
    List<JobConfig> claimDueJobs() {
        String sql = "enabled = true and ownerId is null and nextRunAt <= now() order by nextRunAt";
        List<JobConfig> list = JobConfig.<JobConfig>find(sql).page(Page.ofSize(MAX_CONCURRENT_JOBS)).list();
        if (!list.isEmpty()) {
            log.debug("Claimed [{}] due jobs", list.size());
        }
        return list;
    }

    List<JobConfig> findSuspiciousJobs(@NonNull Set<Long> jobIds) {
        Instant maxRunTime = Instant.now().minus(JobConstants.MAX_JOB_RUNTIME);
        return JobConfig.list("id in ?1 and startedAt < ?2", jobIds, maxRunTime);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void startJob(long jobId) {
        JobConfig job = JobConfig.<JobConfig>find("id = ?1", jobId).firstResult();
        if (job == null) {
            log.warn("Owner [{}] unable to acquire job [{}] (row no longer exists)", OWNER_ID, jobId);
            return;
        }
        job.startedAt = Instant.now();
        job.ownerId = OWNER_ID;
        job.persist();
        log.debug("Owner [{}] acquired job [{}]", OWNER_ID, job);
    }

    /**
     * Finishes the job, schedules its next eligible run, and releases its ownership.
     *
     * @param jobId  id of the job that finished running
     * @param status the status of the job
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void finishJob(long jobId, @NonNull JobStatus status) {
        JobConfig job = JobConfig.<JobConfig>find("id = ?1 and ownerId = ?2", jobId, OWNER_ID).firstResult();

        // warn user about unexpected behavior
        if (job == null) {
            log.warn("Could not finish job [{}] with owner [{}]. " +
                    "This is unexpected behavior and may indicate a concurrency issue. " +
                    "Please investigate :(", jobId, OWNER_ID);
            return;
        }

        Instant now = Instant.now();

        // complete job
        job.lastRunAt = now;
        job.lastRunStatus = status;
        job.nextRunAt = now.plusSeconds(job.intervalSeconds);

        // no longer working on it
        job.startedAt = null;
        job.ownerId = null;

        job.persist();

        log.debug("Job [{}] rescheduled to [{}]", job, job.nextRunAt.truncatedTo(ChronoUnit.SECONDS));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void cleanupOrphanedJobs() {
        String sql = "ownerId = null, startedAt = null where ownerId is not null and startedAt < ?1";
        Instant threshold = Instant.now().minus(CLEANUP_ORPHANED_AFTER);
        int updated = JobConfig.update(sql, threshold);
        if (updated > 0) {
            log.warn("Cleaned up [{}] orphaned jobs with stale ownership", updated);
        }
    }
}
