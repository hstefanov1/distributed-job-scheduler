package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
class JobFactory {

    private final Map<JobName, JobProcessor> processors;

    JobFactory(Instance<JobProcessor> instances) {
        this.processors = instances.stream()
                .collect(Collectors.toMap(JobProcessor::name, Function.identity()));
    }

    JobProcessor get(JobName name) {
        JobProcessor processor = processors.get(name);
        if (processor == null) {
            // to fix that, make sure:
            // 1) your processor implements the interface JobProcessor
            // 2) your processor#name() returns the expected JobName
            // 3) your processor is public and application-scoped
            throw new IllegalStateException("No JobProcessor registered for job [%s]".formatted(name));
        }
        return processor;
    }
}
