package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.runtime.Shutdown;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages PostgreSQL session-level advisory locks (exclusive, non-blocking) keyed by {@link JobName}.
 * <p>
 * Each held lock pins one {@link Connection} from the pool until instance shutdown.
 */
@Slf4j
@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class JobLock {

    /**
     * Data source used to get dedicated database connections for advisory locking.
     */
    private final DataSource dataSource;

    /**
     * Map of currently acquired job locks and their associated active database connections.
     */
    private final ConcurrentHashMap<JobName, Connection> locks = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire the lock for the given job name, non-blocking.
     *
     * @param jobName the name of the job to lock
     * @return {@code true} if the lock is held by this instance (newly acquired or already held),
     * {@code false} if it's currently held elsewhere
     */
    boolean tryAcquire(@NonNull JobName jobName) {
        if (isAlreadyAcquired(jobName)) {
            log.debug("Lock for job [{}] already acquired", jobName);
            return true;
        }

        log.debug("Lock for job [{}] being acquired", jobName);
        Connection connection = getConnection();
        boolean locked;
        try {
            locked = tryAdvisoryLock(connection, jobName.getId());
        } catch (RuntimeException e) {
            closeSafely(connection); // avoid connection leak if any exception
            log.warn("Lock for job [{}] failed acquiring", jobName, e);
            throw e;
        }
        log.debug("Lock for job [{}] {}", jobName, locked ? "successfully acquired" : "not acquired");

        if (!locked) {
            closeSafely(connection); // unable to acquire lock then close connection
            return false;
        }

        locks.put(jobName, connection); // keep connection open to held lock
        return true;
    }

    /**
     * Releases the active lock for the given job name.
     *
     * @param jobName the name of the job to unlock
     */
    void release(@NonNull JobName jobName) {
        Connection connection = locks.remove(jobName);
        if (connection == null) {
            log.warn("Lock for job [{}] unreleased (lock held by another instance)", jobName);
            return;
        }

        log.debug("Lock for job [{}] being released", jobName);
        try {
            advisoryUnlock(connection, jobName.getId());
        } finally {
            closeSafely(connection);
        }
        log.debug("Lock for job [{}] successfully released", jobName);
    }

    /**
     * Validates the physical connection's health. If the connection is closed or invalid, the lock tracker is
     * cleaned up and {@code false} is returned.
     *
     * @param jobName the name of the job to check
     * @return {@code true} if the lock is held and the connection is healthy; {@code false} otherwise
     */
    boolean isAlreadyAcquired(JobName jobName) {
        Connection connection = locks.get(jobName);
        if (connection == null) {
            return false;
        }
        try {
            if (connection.isValid(1)) {
                return true;
            }
            log.warn("Lock for job [{}] considered lost (connection is no longer valid)", jobName);
        } catch (SQLException e) {
            log.warn("Lock for job [{}] considered lost (failed to validate connection: {})", jobName, e.getMessage());
        }
        closeSafely(locks.remove(jobName));
        return false;
    }

    /**
     * Low-level helper executing PostgreSQL's {@code pg_try_advisory_lock} function.
     * <p>
     * Acquires a session-level double-key lock using the system-wide namespace and the job's ID.
     *
     * @param connection the database connection to run the query on
     * @param key        the unique identifier of the job
     * @return {@code true} if the PostgreSQL advisory lock was successfully acquired; {@code false} otherwise
     * @throws IllegalStateException if a database access error occurs during query execution
     */
    boolean tryAdvisoryLock(Connection connection, int key) {
        String sql = "SELECT pg_try_advisory_lock(?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, JobConstants.LOCK_NAMESPACE);
            ps.setInt(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed executing query [%s]".formatted(sql), e);
        }
    }

    /**
     * Low-level helper executing PostgreSQL's {@code pg_advisory_unlock} function.
     * <p>
     * Explicitly unlocks the session-level advisory lock using the namespace and job's ID.
     *
     * @param connection the database connection to run the query on
     * @param key        the unique, stable identifier of the job
     * @throws IllegalStateException if a database access error occurs during query execution
     */
    void advisoryUnlock(Connection connection, int key) {
        String sql = "SELECT pg_advisory_unlock(?, ?)";
        try {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, JobConstants.LOCK_NAMESPACE);
                ps.setInt(2, key);
                ps.execute();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed executing query [%s]".formatted(sql), e);
        }
    }

    /**
     * Obtains a raw SQL {@link Connection} from the configured {@link DataSource}.
     *
     * @return a new active database connection
     * @throws IllegalStateException if a database access error occurs
     */
    Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed getting data-source connection", e);
        }
    }

    /**
     * Safely closes the given database connection, swallowing any {@link SQLException}.
     *
     * @param connection the connection to close, may be {@code null}
     */
    void closeSafely(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // we don't care the reason
            }
        }
    }

    /**
     * Releases any locks held at shutdown.
     * <p>
     * PostgreSQL would release them anyway once the connection closes, but do it explicitly here for
     * clarity and to avoid relying on that implicit behavior.
     */
    @Shutdown
    void onShutdown() {
        log.debug("Releasing [{}] job locks", locks.size());
        ArrayList<JobName> copy = new ArrayList<>(locks.keySet());
        copy.forEach(this::release);
    }
}
