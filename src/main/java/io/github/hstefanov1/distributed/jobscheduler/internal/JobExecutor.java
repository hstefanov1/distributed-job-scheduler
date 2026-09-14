package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobExecutor {

  private final JobLock lock;
  private final JobFactory factory;
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

    try {
      JobProcessor processor = factory.get(jobName);
      JobContext context = createContext(job);
      log.debug("Job [{}] processing", jobName);
      processor.process(context);
    } finally {
      reschedule(job.id);
      running.remove(jobName);
    }

    log.debug("Job [{}] completed", jobName);
  }

  JobContext createContext(JobConfig job) {
    return new JobContext(
        job.jobName,
        job.batchSize
    );
  }

  void reschedule(@NonNull Long jobId) {
    QuarkusTransaction.requiringNew().run(() -> {
      JobConfig job = JobConfig.findById(jobId); // because instance is detached
      Instant now = Instant.now();
      job.lastRunAt = now;
      job.nextRunAt = now.plusSeconds(job.intervalSeconds);
      log.debug("Job [{}] rescheduled to [{}]", job.jobName,
          job.nextRunAt.truncatedTo(ChronoUnit.SECONDS));
      job.persist();
    });
  }
}
