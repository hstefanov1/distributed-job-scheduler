package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

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

    JobProcessor get(JobName jobName) {
        JobProcessor processor = processors.get(jobName);
        if (processor == null) {
            // to fix that, make sure:
            // 1) your processor implements the interface JobProcessor
            // 2) your processor#name() returns the expected JobName
            // 3) your processor is public and application-scoped
            throw new IllegalStateException("No job processor registered for job name [%s]".formatted(jobName));
        }
        return processor;
    }
}
