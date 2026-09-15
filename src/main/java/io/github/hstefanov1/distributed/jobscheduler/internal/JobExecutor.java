package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobExecutor {

    private final JobLock lock;
    private final JobFactory factory;
    private final JobRepository repository;
    private final Set<JobName> running = ConcurrentHashMap.newKeySet();

    /**
     * Attempts to acquire a PostgreSQL advisory lock for this job. If acquired, the lock is held for
     * the lifetime of this instance and the job is started. If another replica already holds it, the
     * job does not run on this instance.
     */
    public void execute(@NonNull JobConfig job) {
        JobName jobName = job.jobName;
        log.debug("Job [{}] starting", jobName);

        if (!lock.tryAcquire(jobName)) {
            log.debug("Job [{}] aborted (lock is held by another instance)", jobName);
            return;
        }

        if (!running.add(jobName)) {
            log.debug("Job [{}] still in progress, skipping this tick", jobName);
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
}
