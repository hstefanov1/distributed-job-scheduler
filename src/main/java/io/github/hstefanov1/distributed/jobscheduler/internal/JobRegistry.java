package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static io.github.hstefanov1.distributed.jobscheduler.internal.JobConstants.*;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobRegistry {

    private final JobExecutor executor;
    private final JobRepository repository;

    @Scheduled(delay = SCHEDULED_DELAY, every = DISPATCH_JOBS_EVERY)
    void dispatchJobs() {
        repository.claimDueJobs().forEach(executor::submit);
    }

    @Scheduled(delay = SCHEDULED_DELAY, every = CLEANUP_ORPHANED_JOBS_EVERY)
    void cleanupOrphanedJobs() {
        repository.cleanupOrphanedJobs();
    }

    @Scheduled(delay = SCHEDULED_DELAY, every = REPORT_SUSPICIOUS_JOBS_EVERY)
    void reportSuspiciousJobs() {
        Set<Long> running = executor.getRunning();
        if (running.isEmpty()) {
            return;
        }
        List<JobConfig> suspicious = repository.findSuspiciousJobs(running);
        for (JobConfig job : suspicious) {
            log.warn("Job [{}] exceeded max runtime [{} min] (potential hang)", job, MAX_JOB_RUNTIME.toMinutes());
            // add your metric/alert here
        }
    }
}
