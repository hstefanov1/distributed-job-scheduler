package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobRegistry {

    private static final int BATCH_LIMIT = 10; // start a max of ten jobs per run

    private final JobRepository repository;
    private final JobExecutor executor;

    // delay scheduler two minutes to warm up the instance and only then dispatch jobs
    @Scheduled(delay = 2, every = "30s")
    void dispatchJobs() {
        // TODO: Refactor/Rethink heartbeat-renewal race for long-running jobs.
        //  owner_heartbeat_at is only set once, at JobRepository#claimJobs(..), which means if job runs longer
        //  than the staleness window (2 minutes), the row looks "due + stale" to EVERY replica. That means that
        //  whichever replica's tick fires first will win the UPDATE and stamp itself as owner_id/owner_heartbeat_at,
        //  even if it isn't the advisory lock holder and isn't running anything. This makes the real owner stop
        //  seeing this row forever and lets the system lie about who the real owner/worker of this job is.
        //  --
        //  This does NOT cause double executions, because pg_try_advisory_lock is the real cross-session
        //  mutex, so an impostor replica's lock attempt will correctly fail and back off.
        //  But it does mean owner_id/owner_heartbeat_at can change across replicas that aren't
        //  actually doing anything for the entire duration of any job exceeding the staleness
        //  window, corrupting the ownership metadata for monitoring/alerting/ops tools.
        //  --
        //  POSSIBLE FIX: the real owner must actively renew its owner_heartbeat_at on its own timer WHILE
        //  executing (example: every 15s - need to be inside the 2 minute window), independent
        //  of the polling tick, so the row never goes stale while genuinely owned.
        //  As a defense, make the final UPDATE conditional on "WHERE owner_id = :self" and log
        //  or increment a metric if it affects 0 rows, which would indicate that the ownership was stolen out
        //  from under a live job, which, by the way, should be impossible once the renewal timer is in place, but is
        //  worth detecting if it ever regresses.
        //  --
        //  OPEN DECISION: renew via one periodic sweep over the JobExecutor#running set covering all jobs
        //  this replica is currently executing vs. a per-job scheduled renewal started at
        //  lock-acquisition time and canceled in the same finally block as the rest of cleanup.
        repository.claimJobs(BATCH_LIMIT).forEach(executor::submit);
    }
}
