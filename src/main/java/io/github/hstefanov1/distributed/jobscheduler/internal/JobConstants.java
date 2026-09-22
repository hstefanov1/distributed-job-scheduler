package io.github.hstefanov1.distributed.jobscheduler.internal;

import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * Shared constant values and system-wide configuration defaults for the distributed job scheduler.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
class JobConstants {

    // current replica/instance name
    static final String OWNER_ID = resolveOwnerId();

    // used to isolate advisory locks from other apps (e.g., liquibase)
    static final int LOCK_NAMESPACE = "distributed-job-scheduler".hashCode() & Integer.MAX_VALUE;

    // delay schedulers to warm up the instance and only then do things
    static final long SCHEDULED_DELAY = 2;

    // scheduled every
    static final String DISPATCH_JOBS_EVERY = "30s"; // dispatch jobs every 30 seconds
    static final String CLEANUP_ORPHANED_JOBS_EVERY = "10m"; // low frequency (not a hot path) a rare-case safety net
    static final String REPORT_SUSPICIOUS_JOBS_EVERY = "1m"; // report suspicious jobs every minute
    static final String REPORT_FAILING_JOBS_EVERY = "30m"; // report failing jobs every thirty minutes

    // general configs
    static final int MAX_CONCURRENT_JOBS = 10; // start max jobs per replica per run
    static final int THRESHOLD_FAILED_ATTEMPTS = 5; // above this value an alert must be triggered
    static final Duration THRESHOLD_MAX_RUNTIME = Duration.ofMinutes(30); // watchdog threshold for stuck-job detection
    static final Duration CLEANUP_ORPHANED_AFTER = Duration.ofHours(2); // well above MAX_JOB_RUNTIME

    /**
     * Resolves the unique identifier (owner ID) of the current scheduler replica instance.
     * <p>
     * It attempts to read the environment variables (like {@code HOSTNAME} standard in Kubernetes),
     * falling back to combining the local machine's network hostname and the active OS Process ID (PID).
     *
     * @return a non-null, unique String identifying this specific process/host
     */
    static String resolveOwnerId() {
        Config config = ConfigProvider.getConfig();
        if (config != null) {
            String podName = config.getOptionalValue("hostname", String.class).orElse(null);
            if (podName != null && !podName.isBlank()) {
                return podName;
            }
        }

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return "local-%s-pid#%s".formatted(hostname, pid);
        } catch (UnknownHostException e) {
            return "local-unknown-pid#0";
        }
    }
}
