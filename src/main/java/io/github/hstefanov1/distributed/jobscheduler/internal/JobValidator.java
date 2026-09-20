package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class JobValidator {

    private final JobFactory factory;

    void validateJobNameIds() {
        log.info("Validating job name IDs");
        Set<Integer> ids = new HashSet<>();
        for (JobName jobName : JobName.values()) {
            if (!ids.add(jobName.getId())) {
                throw new IllegalStateException("Found duplicate ID for job [%s]".formatted(jobName));
            }
        }
    }

    void validateJobProcessors() {
        log.info("Validating job processors");

        // retrieve all jobs
        List<JobConfig> jobs;
        try {
            jobs = JobConfig.<JobConfig>findAll().list();
        } catch (Exception e) {
            String error = "Failed to load job configs at startup";
            String fix = "Check database connectivity and that every job_name value matches a JobName enum constant";
            throw new IllegalStateException("%s. %s".formatted(error, fix), e);
        }

        // validate processors for each job
        for (JobConfig job : jobs) {
            factory.get(job.jobName); // throws exception if processor not found
        }
    }

    @Startup
    void onStart() {
        validateJobNameIds();
        validateJobProcessors();
    }
}
