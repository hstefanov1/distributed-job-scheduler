package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

  @Column(name = "batch_size", nullable = false)
  public Integer batchSize;

  @Column(name = "interval_seconds", nullable = false)
  public Long intervalSeconds;

  @Column(name = "enabled", nullable = false)
  public Boolean enabled;

  @Column(name = "next_run_at")
  public Instant nextRunAt;

  @Column(name = "last_run_at")
  public Instant lastRunAt;

  @Version
  @Column(name = "version", nullable = false)
  public Long version = 0L;
}
