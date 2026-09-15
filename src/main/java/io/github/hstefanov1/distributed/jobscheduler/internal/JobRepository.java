package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@ApplicationScoped
@NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class JobRepository {

    private static final String OWNER_ID = resolveOwnerId();

    private static String resolveOwnerId() {
        String podName = System.getenv("HOSTNAME"); // k8s sets this by default
        if (podName != null && !podName.isBlank()) {
            return podName;
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return "local-%s-pid#%s".formatted(hostname, pid);
        } catch (UnknownHostException e) {
            return "local-unknown-pid#-1";
        }
    }

    /**
     * Claims jobs that are enabled and due to run.
     *
     * @param batchLimit maximum number of jobs to claim in this call
     * @return jobs needed to run
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<JobConfig> claimJobs(int batchLimit) {
        // claims a batch of enabled jobs that are due to run and whose current
        // ownership is stale. SKIP LOCKED allows concurrent schedulers to claim
        // different jobs without waiting on jobs currently locked by another scheduler.
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
                      AND owner_heartbeat_at <= now() - interval '2 minutes'
                    ORDER BY next_run_at
                    LIMIT :batchLimit
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id
                """;
        List<?> rows = JobConfig.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("ownerId", OWNER_ID)
                .setParameter("batchLimit", batchLimit)
                .getResultList();

        List<Long> jobIds = rows.stream().map(r -> ((Number) r).longValue()).toList();
        if (!jobIds.isEmpty()) {
            log.debug("Claimed [{}] due jobs for owner [{}]", jobIds.size(), OWNER_ID);
        }

        return JobConfig.findByIds(jobIds);
    }

    /**
     * Marks the job as completed, schedules its next eligible run, and releases its ownership.
     *
     * @param jobId id of the job that finished running
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void completeJob(long jobId) {
        JobConfig job = JobConfig.<JobConfig>find("id = ?1 and ownerId = ?2", jobId, OWNER_ID).firstResult();

        // warn user about unexpected behavior
        if (job == null) {
            log.warn("Could not complete job [{}] with owner [{}]. " +
                    "This is unexpected behavior and may indicate a concurrency issue. " +
                    "Please investigate :(", jobId, OWNER_ID);
            return;
        }

        // complete job
        Instant now = Instant.now();
        job.lastRunAt = now;
        job.nextRunAt = now.plusSeconds(job.intervalSeconds);
        job.ownerId = null; // no longer working on it
        job.persist();

        JobName name = job.jobName;
        Instant nextRunAt = job.nextRunAt.truncatedTo(ChronoUnit.SECONDS);
        log.debug("Job [{}] rescheduled to [{}]", name, nextRunAt);
    }
}
