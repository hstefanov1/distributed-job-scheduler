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

    private final DataSource dataSource;
    private final ConcurrentHashMap<JobName, Connection> locks = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire the lock for the given key, non-blocking.
     *
     * @return {@code true} if the lock is held by this instance (newly acquired or already held),
     * {@code false} if it's currently held elsewhere
     */
    boolean tryAcquire(@NonNull JobName key) {
        if (isAlreadyAcquired(key)) {
            log.debug("Lock for job [{}] already acquired", key);
            return true;
        }

        log.debug("Lock for job [{}] being acquired", key);
        Connection connection = getConnection();
        boolean locked;
        try {
            locked = tryAdvisoryLock(connection, key);
        } catch (RuntimeException e) {
            closeSafely(connection); // avoid connection leak if any exception
            log.warn("Lock for job [{}] failed acquiring", key, e);
            throw e;
        }
        log.debug("Lock for job [{}] {}", key, locked ? "acquired" : "not acquired");

        if (!locked) {
            closeSafely(connection); // unable to acquire lock then close connection
            return false;
        }

        locks.put(key, connection); // keep connection open to held lock
        return true;
    }

    void release(@NonNull JobName key) {
        Connection connection = locks.remove(key);
        if (connection == null) {
            log.warn("Lock for job [{}] unreleased (held by another instance)", key);
            return;
        }

        log.debug("Lock for job [{}] being released", key);
        try {
            advisoryUnlock(connection, key);
        } finally {
            closeSafely(connection);
        }
        log.debug("Lock for job [{}] released", key);
    }

    boolean isAlreadyAcquired(JobName key) {
        Connection connection = locks.get(key);
        if (connection == null) {
            return false;
        }
        try {
            if (connection.isValid(1)) {
                return true;
            }
            log.warn("Lock for job [{}] has been lost, caused by: connection no longer valid", key);
        } catch (SQLException e) {
            log.warn("Lock for job [{}] has been lost, caused by: {}", key, e.getMessage());
        }
        closeSafely(locks.remove(key));
        return false;
    }

    boolean tryAdvisoryLock(Connection connection, JobName key) {
        String sql = "SELECT pg_try_advisory_lock(hashtext(?))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed executing query [%s]".formatted(sql), e);
        }
    }

    void advisoryUnlock(Connection connection, JobName key) {
        String sql = "SELECT pg_advisory_unlock(hashtext(?))";
        try {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, key.name());
                ps.execute();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed executing query [%s]".formatted(sql), e);
        }
    }

    Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed getting data-source connection", e);
        }
    }

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
