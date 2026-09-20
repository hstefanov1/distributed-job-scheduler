package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the integrity of the job scheduler configuration and processors on application startup.
 */
@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class JobValidator {

    private final JobRegistry registry;

    /**
     * Validates that all defined {@link JobName} constants are assigned unique integer identifiers.
     * <p>
     * Unique IDs are required to prevent lock collisions, as these integers are passed directly to PostgreSQL
     * as keys for advisory locking.
     *
     * @throws IllegalStateException if a duplicate job identifier is found
     */
    void validateJobNameIds() {
        log.info("Validating job name IDs");
        Set<Integer> ids = new HashSet<>();
        for (JobName jobName : JobName.values()) {
            if (!ids.add(jobName.getId())) {
                throw new IllegalStateException("Found duplicate ID for job [%s]".formatted(jobName));
            }
        }
    }

    /**
     * Validates that a matching {@code JobProcessor} CDI bean is registered for every job configuration row in the database.
     * <p>
     * This ensures the application will not fail mid-execution due to a missing or unregistered processor bean
     * when a scheduled job becomes due.
     *
     * @throws IllegalStateException if the database lookup fails or if any job config points to an unregistered processor
     */
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
            registry.get(job.jobName); // throws exception if processor not found
        }
    }

    /**
     * Executes the validation checks automatically during application startup.
     * <p>
     * If any validation fails, an exception is thrown, preventing the application from starting in an invalid state.
     */
    @Startup
    void onStart() {
        validateJobNameIds();
        validateJobProcessors();
    }
}
