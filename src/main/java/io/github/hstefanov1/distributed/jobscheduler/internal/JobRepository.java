package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Page;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles all transactional database interactions and query operations for job config and scheduling state.
 */
@Slf4j
@ApplicationScoped
@NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class JobRepository {

    /**
     * Claims active, enabled jobs that are due to run.
     *
     * @return jobs eligible to run, capped at {@value JobConstants#MAX_CONCURRENT_JOBS}
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    List<JobConfig> claimDueJobs() {
        String sql = "enabled = true and ownerId is null and nextRunAt <= ?1 order by nextRunAt";

        List<JobConfig> list = JobConfig.find(sql, Instant.now())
                .page(Page.ofSize(JobConstants.MAX_CONCURRENT_JOBS))

                // tells PostgreSQL that the transaction intends to update rows
                // if another transaction tries to read/write the same rows, it will wait until this transaction ends
                .withLock(LockModeType.PESSIMISTIC_WRITE) // appends FOR UPDATE to the query

                // skips the already locked rows
                // ref: https://docs.hibernate.org/orm/6.5/userguide/html_single/#locking-LockMode
                .withHint("jakarta.persistence.lock.timeout", -2) // appends SKIP LOCKED to the query

                .list();

        if (!list.isEmpty()) {
            log.debug("Claimed [{}] due jobs", list.size());
        }
        return list;
    }

    /**
     * Finds jobs that are currently running and have exceeded the permitted runtime threshold.
     *
     * @param jobIds    the set of database job IDs currently executing
     * @param threshold the threshold runtime permitted
     * @return a list of jobs considered to be potentially hanging/stuck
     */
    List<JobConfig> findSuspiciousJobs(@NonNull Set<Long> jobIds, @NotNull Duration threshold) {
        Instant maxRunTime = Instant.now().minus(threshold);
        String sql = "id in ?1 and ownerId is not null and startedAt < ?2";
        return JobConfig.list(sql, jobIds, maxRunTime);
    }

    /**
     * Marks a job as actively running by setting its start time and recording the current replica
     * instance as owner ID.
     *
     * @param jobId the database ID of the job config to acquire
     * @throws IllegalStateException if the job no longer exists at this point. This is not expected
     *                               to happen under normal operation, since the id was obtained from
     *                               a row claimed via {@link #claimDueJobs()}
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void startJob(long jobId) {
        JobConfig job = JobConfig.findById(jobId);
        if (job == null) {
            String message = "Owner [%s] can't start the job id [%s] because this job id no longer exists";
            throw new IllegalStateException(message.formatted(JobConstants.OWNER_ID, jobId));
        }
        job.startedAt = Instant.now();
        job.ownerId = JobConstants.OWNER_ID;
        job.persist();
        log.debug("Owner [{}] acquired job [{}]", JobConstants.OWNER_ID, job);
    }

    /**
     * Finishes the job, schedules its next eligible run, and releases its ownership.
     *
     * @param jobId     id of the job that finished running
     * @param status    the status of the job
     * @param exception the exception caused the job to fail (can be null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void finishJob(long jobId, @NonNull JobStatus status, @Nullable Throwable exception) {
        JobConfig job = JobConfig.find("id = ?1 and ownerId = ?2", jobId, JobConstants.OWNER_ID).firstResult();
        if (job == null) {
            log.error("Owner [{}] couldn't finish job id [{}] because ownership no longer matches. " +
                    "This is unexpected behavior and may indicate a concurrency issue. " +
                    "Please investigate :(", JobConstants.OWNER_ID, jobId);
            return;
        }

        Instant now = Instant.now();

        // complete job
        job.lastRunAt = now;
        job.lastRunStatus = status;

        // extract exception message
        if (JobStatus.FAILED.equals(status)) {
            Optional<String> message = Optional.ofNullable(exception)
                    .map(Throwable::getMessage)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(ex -> ex.length() > 255 ? ex.substring(0, 255) : ex);
            if (message.isPresent()) {
                job.lastRunException = message.get();
            } else {
                job.lastRunException = null;
                log.warn("Job [{}] failed but no exception message was captured (lastRunException set null)", jobId);
            }
        }

        job.nextRunAt = now.plusSeconds(job.intervalSeconds);

        // no longer working on it
        job.startedAt = null;
        job.ownerId = null;

        job.persist();
        log.debug("Job [{}] rescheduled to [{}]", job, job.nextRunAt.truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * Detects and recovers orphaned jobs marked as running with an active owner and start time,
     * but whose start time has exceeded the configured cleanup threshold {@link JobConstants#CLEANUP_ORPHANED_AFTER}.
     * <p>
     * Clears their ownership and start time so that they can be claimed and executed again.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void cleanupOrphanedJobs() {
        String sql = "ownerId = null, startedAt = null where ownerId is not null and startedAt < ?1";
        Instant threshold = Instant.now().minus(JobConstants.CLEANUP_ORPHANED_AFTER);
        int updated = JobConfig.update(sql, threshold);
        if (updated > 0) {
            log.warn("Cleaned up [{}] orphaned jobs with stale ownership", updated);
        }
    }
}
