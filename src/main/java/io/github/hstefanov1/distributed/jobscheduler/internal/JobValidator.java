package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobValidator {

    private final JobFactory factory;

    @Startup
    void onStart() {
        log.info("Validating jobs");
        List<JobConfig> jobs = getJobs(); // throws exception if job_name not exist
        for (JobConfig job : jobs) {
            factory.get(job.jobName); // throws exception if processor not found
        }
        log.info("Jobs validated successfully");
    }

    List<JobConfig> getJobs() {
        try {
            return JobConfig.<JobConfig>findAll().list();
        } catch (Exception e) {
            throw new IllegalStateException("A job_name may refer to a non-existent enum value", e);
        }
    }
}
