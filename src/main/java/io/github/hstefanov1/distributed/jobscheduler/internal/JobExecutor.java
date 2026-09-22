package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import io.quarkus.runtime.Shutdown;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Executes scheduled jobs asynchronously using virtual threads while managing system-wide concurrency limits.
 */
@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobExecutor {

    // parallel job configs
    private final Semaphore semaphore = new Semaphore(JobConstants.MAX_CONCURRENT_JOBS);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final JobLock lock;
    private final JobRegistry registry;
    private final JobRepository repository;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final Map<JobName, AtomicInteger> failing = new ConcurrentHashMap<>();

    /**
     * Submits the job for asynchronous execution on a virtual thread,
     * respecting a maximum concurrency of {@value JobConstants#MAX_CONCURRENT_JOBS} jobs.
     * <p>
     * If the job is already active/running on this replica, the submission is skipped to prevent overlap.
     *
     * @param job the job config to execute
     */
    void submit(@NonNull JobConfig job) {
        if (!running.add(job.id)) {
            log.debug("Job [{}] still in progress (run skipped)", job);
            return;
        }

        // submit to a virtual thread
        log.debug("Job [{}] starting", job);
        executor.submit(() -> {
            boolean acquired = false;
            try {
                semaphore.acquire();
                acquired = true;
                execute(job);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Job [{}] failed with an unexpected exception", job, e);
            } finally {
                if (acquired) {
                    semaphore.release();
                }
            }
        });
    }

    /**
     * Attempts to acquire a PostgreSQL advisory lock for this job. If acquired, the lock is held for
     * the lifetime of this instance and the job is started. If another replica already holds it, the
     * job does not run on this instance.
     *
     * @param job the job config to run
     */
    void execute(@NonNull JobConfig job) {
        Long jobId = job.id;
        JobName jobName = job.jobName;

        try {
            if (!lock.tryAcquire(jobName)) {
                log.debug("Job [{}] aborted (lock is held by another instance)", job);
                return;
            }

            // assign ownerId and startAt fields
            repository.startJob(jobId);

            // start the job business logic
            Throwable exception = null;
            JobStatus status = JobStatus.FAILED;
            try {
                JobProcessor processor = registry.get(jobName);
                JobContext context = createContext(job);
                log.debug("Job [{}] initialized", job);
                processor.process(context);
                status = JobStatus.COMPLETED;
                failing.remove(jobName);
            } catch (Throwable throwable) {
                failing.computeIfAbsent(jobName, name -> new AtomicInteger()).incrementAndGet();
                exception = throwable;
                throw throwable;
            } finally {
                repository.finishJob(jobId, status, exception);
                log.debug("Job [{}] finished with status [{}]", job, status);
            }
        } finally {
            running.remove(jobId);
        }
    }

    /**
     * Creates the execution context containing runtime environment variables for the target {@link JobProcessor}.
     *
     * @param job the job config to build the context from
     * @return a new {@link JobContext} instance
     */
    JobContext createContext(@NonNull JobConfig job) {
        return new JobContext(
                job.jobName,
                JobConstants.OWNER_ID,
                job.batchSize
        );
    }

    /**
     * Retrieves an immutable snapshot of job database IDs currently running on this replica.
     *
     * @return a read-only set of active job IDs
     */
    Set<Long> getRunning() {
        return Set.copyOf(running);
    }

    /**
     * Retrieves an immutable snapshot of jobs currently failing, along with their consecutive failure count.
     * <p>
     * A job is removed from this map as soon as it completes successfully, so only jobs that have failed
     * since their last successful execution are present.
     *
     * @return a read-only map of job name to failure count; empty if no jobs are currently failing
     */
    public Map<JobName, Integer> getFailing() {
        return failing.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * Gracefully shuts down the executor service upon application shutdown.
     */
    @Shutdown
    void onShutdown() {
        //
        // This is a two-phase shutdown pattern by Oracle ;)
        // Ref: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
        //
        log.info("Job executor shutting down");
        executor.shutdown(); // stop accepting new jobs
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("Job executor did not terminate in time! Forcing shutdown...");
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("Job executor still running after forced shutdown");
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        log.info("Job executor shutdown completed");
    }
}
