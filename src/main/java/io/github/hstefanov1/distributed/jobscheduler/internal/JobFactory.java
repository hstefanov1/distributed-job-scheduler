package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
class JobFactory {

    private final Map<JobName, JobProcessor> processors;

    JobFactory(Instance<JobProcessor> instances) {
        this.processors = instances.stream().collect(Collectors.toMap(JobProcessor::name, Function.identity()));
    }

    JobProcessor get(JobName name) {
        JobProcessor processor = processors.get(name);
        if (processor == null) {
            // to fix that, make sure:
            // 1) your processor implements the interface JobProcessor
            // 2) your processor#name() returns the expected JobName
            // 3) your processor is public and application-scoped
            throw new IllegalStateException("No job processor registered for job name [%s]".formatted(name));
        }
        return processor;
    }

    @Startup
    void onStart() {
        log.info("Validating job processors");

        // retrieve all jobs
        List<JobConfig> jobs;
        try {
            jobs = JobConfig.<JobConfig>findAll().list();
        } catch (Exception e) {
            throw new IllegalStateException("A job name may refer to a non-existent enum value", e);
        }

        // validate processors for each job
        for (JobConfig job : jobs) {
            get(job.jobName); // throws exception if processor not found
        }

        log.info("Validated job processors");
    }
}
