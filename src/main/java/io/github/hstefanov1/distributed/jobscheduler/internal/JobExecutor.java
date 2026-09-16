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

import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobExecutor {

    // parallel jobs configs
    private static final int MAX_CONCURRENT_JOBS = 10;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_JOBS);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final JobLock lock;
    private final JobFactory factory;
    private final JobRepository repository;
    private final Set<JobName> running = ConcurrentHashMap.newKeySet();

    /**
     * Submits the job for asynchronous execution on a virtual thread,
     * respecting a maximum concurrency of {@value #MAX_CONCURRENT_JOBS} jobs.
     *
     * @param job the job to execute
     */
    public void submit(@NonNull JobConfig job) {
        executor.submit(() -> {
            boolean acquired = false;
            try {
                semaphore.acquire();
                acquired = true;
                execute(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
     */
    void execute(JobConfig job) {
        JobName jobName = job.jobName;
        log.debug("Job [{}] starting", jobName);

        if (!lock.tryAcquire(jobName)) {
            log.debug("Job [{}] aborted (lock is held by another instance)", jobName);
            return;
        }

        if (!running.add(jobName)) {
            log.debug("Job [{}] still in progress (run skipped)", jobName);
            return;
        }

        start(job);
    }

    void start(JobConfig job) {
        JobName jobName = job.jobName;
        try {
            JobProcessor processor = factory.get(jobName);
            JobContext context = createContext(job);
            log.debug("Job [{}] started", jobName);
            processor.process(context);
        } finally {
            repository.completeJob(job.id);
            running.remove(jobName);
            log.debug("Job [{}] completed", jobName);
        }
    }

    JobContext createContext(JobConfig job) {
        return new JobContext(
                job.jobName,
                job.ownerId,
                job.batchSize
        );
    }

    @Shutdown
    void onShutdown() {
        //
        // This is a two-phase shutdown pattern by Oracle ;)
        // Ref: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
        //
        log.info("Shutting down job executor");
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
