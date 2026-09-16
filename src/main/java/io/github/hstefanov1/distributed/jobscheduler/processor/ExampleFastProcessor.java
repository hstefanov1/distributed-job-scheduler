package io.github.hstefanov1.distributed.jobscheduler.processor;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import static io.github.hstefanov1.distributed.jobscheduler.api.JobName.EXAMPLE_FAST;

@Slf4j
@ApplicationScoped
public class ExampleFastProcessor implements JobProcessor {

    @Override
    public JobName name() {
        return EXAMPLE_FAST;
    }

    @Override
    public void process(JobContext context) {
        log.info("Processing example fast");
        // no workload
        log.info("Processed example fast");
    }
}
