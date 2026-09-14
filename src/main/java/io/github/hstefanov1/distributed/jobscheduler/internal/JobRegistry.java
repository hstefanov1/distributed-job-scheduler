package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobRegistry {

  private final JobExecutor executor;

  @Blocking
  @Scheduled(every = "30s")
  void dispatchJobs() {
    Instant now = Instant.now();
    String sql = "enabled = true and (nextRunAt is null or nextRunAt <= ?1)";
    JobConfig.<JobConfig>find(sql, now).list().forEach(executor::execute);
  }
}
