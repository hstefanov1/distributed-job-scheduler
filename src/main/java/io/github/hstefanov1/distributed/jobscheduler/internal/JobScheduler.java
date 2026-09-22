package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.hstefanov1.distributed.jobscheduler.internal.JobConstants.*;

/**
 * Orchestrates the periodic scheduling of job dispatching, recovery, and watchdog reporting tasks.
 */
@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobScheduler {

    private final JobExecutor executor;
    private final JobRepository repository;

    /**
     * Periodically queries the database for active, enabled jobs that are due to execute,
     * attempts to lock/claim them, and submits them to the execution queue.
     */
    @Scheduled(delay = SCHEDULED_DELAY, every = DISPATCH_JOBS_EVERY)
    void dispatchJobs() {
        repository.claimDueJobs().forEach(executor::submit);
    }

    /**
     * Periodically checks the repository for orphaned jobs—jobs left in an active state owned by a replica
     * that has crashed or failed to complete execution in a reasonable period.
     * <p>
     * Resets their execution state and next execution schedule.
     */
    @Scheduled(delay = SCHEDULED_DELAY, every = CLEANUP_ORPHANED_JOBS_EVERY)
    void cleanupOrphanedJobs() {
        repository.cleanupOrphanedJobs();
    }

    /**
     * Watchdog task that monitors jobs currently executing on the local instance.
     * <p>
     * Compares active execution runtimes against {@link JobConstants#MAX_JOB_RUNTIME} to identify and log
     * warnings for potentially hanging/stuck executions.
     */
    @Scheduled(delay = SCHEDULED_DELAY, every = REPORT_SUSPICIOUS_JOBS_EVERY)
    void reportSuspiciousJobs() {
        Set<Long> running = executor.getRunning();
        if (running.isEmpty()) {
            return;
        }
        List<JobConfig> suspicious = repository.findSuspiciousJobs(running);
        for (JobConfig job : suspicious) {
            log.warn("Job [{}] exceeded max runtime [{}min], please take actions (potential hang)", job, MAX_JOB_RUNTIME.toMinutes());
            // add your metric/alert here
        }
    }

    /**
     * Watchdog task that monitors jobs currently failing above the configured threshold on the local instance.
     * <p>
     * Each job whose consecutive failure count exceeds {@link JobConstants#THRESHOLD_FAILED_JOBS},
     * logs an error.
     * <p>
     * Jobs failing at or below the threshold are ignored.
     */
    @Scheduled(delay = SCHEDULED_DELAY, every = REPORT_FAILING_JOBS_EVERY)
    void reportFailingJobs() {
        Map<JobName, Integer> failing = executor.getFailing();
        if (failing.isEmpty()) {
            return;
        }
        for (Map.Entry<JobName, Integer> failed : failing.entrySet()) {
            JobName job = failed.getKey();
            Integer count = failed.getValue();
            if (count > THRESHOLD_FAILED_JOBS) {
                log.error("Job [{}] is failing (current count [{}] exceeds threshold [{}]), please take actions",
                        job, count, THRESHOLD_FAILED_JOBS);
                // add your metric/alert here
            }
        }
    }
}
