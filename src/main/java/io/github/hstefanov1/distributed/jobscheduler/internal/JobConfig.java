package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Database entity representing the configuration and execution state of a scheduled job.
 */
@Entity
@Table(name = "job_config")
class JobConfig extends PanacheEntityBase {

    /**
     * Unique database identifier for this configuration row.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * The unique business identifier mapping this configuration to its {@link JobName}.
     */
    @Column(name = "job_name", nullable = false, updatable = false, unique = true)
    @Enumerated(EnumType.STRING)
    public JobName jobName;

    /**
     * The identifier of the scheduler replica instance currently holding the lock on and executing this job.
     * <p>
     * If {@code null}, the job is currently unlocked and eligible for execution by any healthy replica.
     */
    @Column(name = "owner_id")
    public String ownerId;

    /**
     * The timestamp when the current execution of this job started.
     * <p>
     * This timestamp is used to detect stuck jobs or handle recovery when a replica crashes mid-execution.
     */
    @Column(name = "started_at")
    public Instant startedAt;

    /**
     * The number of items to process per batch for this job.
     * <p>
     * Defaults to {@code 100}.
     */
    @Column(name = "batch_size", nullable = false)
    public int batchSize = 100;

    /**
     * The period, in seconds, between consecutive executions of this job.
     * <p>
     * Defaults to {@code 300} seconds (5 minutes).
     */
    @Column(name = "interval_seconds", nullable = false)
    public int intervalSeconds = 300;

    /**
     * Flag indicating whether this job config is active and eligible for scheduling.
     * <p>
     * Defaults to {@code true}.
     */
    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;

    /**
     * The scheduled timestamp of the next run.
     * <p>
     * The scheduler queries this field to determine which jobs are eligible to be acquired and executed.
     * <p>
     * Defaults to the creation/initiation time.
     */
    @Column(name = "next_run_at", nullable = false)
    public Instant nextRunAt = Instant.now();

    /**
     * The timestamp of when the last execution completed.
     */
    @Column(name = "last_run_at")
    public Instant lastRunAt;

    /**
     * The completion status of the last execution.
     * <p>
     * Defaults to {@link JobStatus#PENDING}.
     */
    @Column(name = "last_run_status", nullable = false)
    public JobStatus lastRunStatus = JobStatus.PENDING;

    /**
     * Returns the name of the job represented by this configuration.
     *
     * @return the {@link String} name of the job
     */
    @Override
    public String toString() {
        return this.jobName.name();
    }
}
