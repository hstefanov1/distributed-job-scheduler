package io.github.hstefanov1.distributed.jobscheduler.processor;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static io.github.hstefanov1.distributed.jobscheduler.api.JobName.EXAMPLE_SLOW;

@Slf4j
@ApplicationScoped
public class ExampleSlowProcessor implements JobProcessor {

    @Override
    public JobName name() {
        return EXAMPLE_SLOW;
    }

    @Override
    public void process(JobContext context) {
        log.info("Processing example slow");
        try {
            long seconds = 150;
            log.info("Workload [{}s]", seconds);
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ignored) {
        }
        log.info("Processed example slow");
    }
}
