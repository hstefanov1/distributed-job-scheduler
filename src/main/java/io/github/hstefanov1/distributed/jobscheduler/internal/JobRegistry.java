package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobRegistry {

    private static final Integer BATCH_LIMIT = 10;

    private final JobRepository repository;
    private final JobExecutor executor;

    @Blocking
    @Scheduled(delay = 2, every = "30s")
        // delay 2 minutes to warm up the instance
    void dispatchJobs() {
        repository.claimDueJobs(BATCH_LIMIT).forEach(executor::execute);
    }
}
