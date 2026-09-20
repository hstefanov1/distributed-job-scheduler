package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "job_config")
class JobConfig extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_name", nullable = false, updatable = false, unique = true)
    @Enumerated(EnumType.STRING)
    public JobName jobName;

    @Column(name = "owner_id")
    public String ownerId;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "batch_size", nullable = false)
    public int batchSize = 100;

    @Column(name = "interval_seconds", nullable = false)
    public int intervalSeconds = 300;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;

    @Column(name = "next_run_at", nullable = false)
    public Instant nextRunAt = Instant.now();

    @Column(name = "last_run_at")
    public Instant lastRunAt;

    @Column(name = "last_run_status", nullable = false)
    public JobStatus lastRunStatus = JobStatus.PENDING;

    @Override
    public String toString() {
        return this.jobName.name();
    }
}
