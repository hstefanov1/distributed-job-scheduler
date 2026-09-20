package io.github.hstefanov1.distributed.jobscheduler.internal;

import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
class JobConstants {

    // current replica/instance name
    static final String OWNER_ID = resolveOwnerId();

    // used to isolate advisory locks from other apps (e.g., liquibase)
    static final int LOCK_NAMESPACE = Math.abs("distributed-job-scheduler".hashCode());

    // delay schedulers to warm up the instance and only then do things
    static final long SCHEDULED_DELAY = 2;

    // scheduled every
    static final String DISPATCH_JOBS_EVERY = "30s"; // dispatch jobs every 30 seconds
    static final String CLEANUP_ORPHANED_JOBS_EVERY = "10m"; // low frequency (not a hot path) a rare-case safety net
    static final String REPORT_SUSPICIOUS_JOBS_EVERY = "1m"; // report suspicious jobs every minute

    // general configs
    static final int MAX_CONCURRENT_JOBS = 10; // start max jobs per replica per run
    static final Duration MAX_JOB_RUNTIME = Duration.ofMinutes(30); // watchdog threshold for stuck-job detection
    static final Duration CLEANUP_ORPHANED_AFTER = Duration.ofHours(2); // well above MAX_JOB_RUNTIME

    private static String resolveOwnerId() {
        String podName = System.getenv("HOSTNAME"); // k8s sets this by default
        if (podName != null && !podName.isBlank()) {
            return podName;
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return "local-%s-pid#%s".formatted(hostname, pid);
        } catch (UnknownHostException e) {
            return "local-unknown-pid#-1";
        }
    }
}
