package io.github.hstefanov1.distributed.jobscheduler.processor;

import static io.github.hstefanov1.distributed.jobscheduler.api.JobName.DEACTIVATE_EXPIRED;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class DeactivateExpiredProcessor implements JobProcessor {

  @Override
  public JobName name() {
    return DEACTIVATE_EXPIRED;
  }

  @Override
  @SuppressWarnings("java:S2142")
  public void process(JobContext context) {
    log.info("Processing deactivation of expired");

    // some mocked workload
    try {
      long seconds = 36;
      log.info("Processing workload [{}s]", seconds);
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException ignored) {
      // we don't care, it's for test purposes
    }

    log.info("Completed processing deactivation of expired");
  }
}
