package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobRegistry {

    private static final int BATCH_LIMIT = 10; // start a max of ten jobs per run

    private final JobRepository repository;
    private final JobExecutor executor;

    // delay scheduler two minutes to warm up the instance and only then dispatch jobs
    @Scheduled(delay = 2, every = "30s")
    void dispatchJobs() {
        repository.claimJobs(BATCH_LIMIT).forEach(executor::submit);
    }
}
