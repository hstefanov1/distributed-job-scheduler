package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobLockTest {

    @Mock
    private DataSource dataSourceMock;

    @InjectMocks
    private JobLock instance;

    @Test
    @SuppressWarnings("DataFlowIssue")
    void tryAcquire_WhenJobNameIsNull_ThenAnExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.tryAcquire(null));
    }

    @Test
    void tryAcquire_WhenLockIsAlreadyAcquired_ThenTrueIsReturned() {
        JobLock instanceSpy = spy(instance);

        // mocks
        doReturn(true).when(instanceSpy).isAlreadyAcquired(any());

        instanceSpy.tryAcquire(mock(JobName.class));

        // verifications
        verify(instanceSpy, times(1)).isAlreadyAcquired(any());
        verify(instanceSpy, times(0)).getConnection();
        verify(instanceSpy, times(0)).tryAdvisoryLock(any(), anyInt());
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    void tryAcquire_WhenExceptionOnTryAdvisoryLock_ThenAnExceptionIsThrown() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doThrow(IllegalStateException.class).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        doNothing().when(instanceSpy).closeSafely(any());

        // test goal
        assertThrows(IllegalStateException.class, () -> instanceSpy.tryAcquire(JobName.EXAMPLE_FAST));

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), anyInt());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    void tryAcquire_WhenLockIsNotAcquired_ThenFalseIsReturned() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(false).when(instanceSpy).tryAdvisoryLock(any(), anyInt());

        // test goal
        boolean result = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertFalse(result);

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), anyInt());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    void tryAcquire_WhenLockIsAcquired_ThenTrueIsReturned() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());

        // test goal
        boolean result = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertTrue(result);

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), anyInt());
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void release_WhenJobNameIsNull_ThenAnExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.release(null));
    }

    @Test
    void release_WhenLockIsHeldByAnotherInstance_ThenReleaseIsNotCalled() {
        JobLock instanceSpy = spy(instance);
        instanceSpy.release(mock(JobName.class));
        verify(instanceSpy, times(0)).advisoryUnlock(any(), anyInt());
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    void release_WhenLockIsHeldByCurrentInstance_ThenReleaseIsCalled() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        doReturn(false).when(instanceSpy).isAlreadyAcquired(any());
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        JobName jobNameMock = mock(JobName.class);
        boolean locked = instanceSpy.tryAcquire(jobNameMock);
        assertTrue(locked);

        // mock behaviors
        doNothing().when(instanceSpy).advisoryUnlock(any(), anyInt());
        doNothing().when(instanceSpy).closeSafely(any());

        // test goal
        instanceSpy.release(jobNameMock);
        verify(instanceSpy, times(1)).advisoryUnlock(any(), anyInt());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    void isAlreadyAcquired_WhenConnectionIsNull_ThenFalseIsReturned() {
        assertFalse(instance.isAlreadyAcquired(mock(JobName.class)));
    }

    @Test
    @SneakyThrows
    void isAlreadyAcquired_WhenConnectionIsValid_ThenTrueIsReturned() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        JobName jobNameMock = mock(JobName.class);
        boolean locked = instanceSpy.tryAcquire(jobNameMock);
        assertTrue(locked);

        doReturn(true).when(connMock).isValid(1);

        boolean result = instanceSpy.isAlreadyAcquired(jobNameMock);
        assertTrue(result);

        verify(connMock, times(1)).isValid(1);
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    @SneakyThrows
    void isAlreadyAcquired_WhenConnectionIsNotValid_ThenFalseIsReturned() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        JobName jobNameMock = mock(JobName.class);
        boolean locked = instanceSpy.tryAcquire(jobNameMock);
        assertTrue(locked);

        doReturn(false).when(connMock).isValid(1);

        boolean result = instanceSpy.isAlreadyAcquired(jobNameMock);
        assertFalse(result);

        verify(connMock, times(1)).isValid(1);
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    @SneakyThrows
    void isAlreadyAcquired_WhenSqlExceptionIsThrown_ThenFalseIsReturned() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        JobName jobNameMock = mock(JobName.class);
        boolean locked = instanceSpy.tryAcquire(jobNameMock);
        assertTrue(locked);

        doThrow(SQLException.class).when(connMock).isValid(1);

        boolean result = instanceSpy.isAlreadyAcquired(jobNameMock);
        assertFalse(result);

        verify(connMock, times(1)).isValid(1);
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    @SneakyThrows
    void tryAdvisoryLock_WhenSuccessfullyAcquired_ThenTrueIsReturned() {
        // mock behaviors
        Connection connMock = mock(Connection.class);
        PreparedStatement psMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        doReturn(psMock).when(connMock).prepareStatement(anyString());
        doReturn(rsMock).when(psMock).executeQuery();
        doReturn(true).when(rsMock).getBoolean(1);

        // test goal
        boolean result = instance.tryAdvisoryLock(connMock, 1);
        assertTrue(result);

        // verifications
        verify(connMock, times(1)).prepareStatement(anyString());
        verify(psMock, times(1)).setInt(1, JobConstants.LOCK_NAMESPACE);
        verify(psMock, times(1)).setInt(2, 1);
        verify(psMock, times(1)).executeQuery();
        verify(rsMock, times(1)).next();
        verify(rsMock, times(1)).getBoolean(anyInt());

        // close
        verify(rsMock, times(1)).close();
        verify(psMock, times(1)).close();
    }

    @Test
    @SneakyThrows
    void tryAdvisoryLock_WhenFailedAcquiring_ThenAnExceptionIsThrown() {
        // query
        String query = "SELECT pg_try_advisory_lock(?, ?)";

        // mock behaviors
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).prepareStatement(anyString());

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.tryAdvisoryLock(connMock, 1));
        String expected = "Failed executing query [%s]".formatted(query);
        assertEquals(expected, e.getMessage());
    }

    @Test
    @SneakyThrows
    void advisoryUnlock_WhenSuccessfullyUnlocked_ThenNoExceptionIsThrown() {
        // mock behaviors
        Connection connMock = mock(Connection.class);
        PreparedStatement psMock = mock(PreparedStatement.class);
        doReturn(psMock).when(connMock).prepareStatement(anyString());

        // test goal
        instance.advisoryUnlock(connMock, 1);

        // verifications
        verify(connMock, times(1)).prepareStatement(anyString());
        verify(psMock, times(1)).setInt(1, JobConstants.LOCK_NAMESPACE);
        verify(psMock, times(1)).setInt(2, 1);
        verify(psMock, times(1)).execute();

        // close
        verify(psMock, times(1)).close();
    }


    @Test
    @SneakyThrows
    void advisoryUnlock_WhenFailedUnlocking_ThenAnExceptionIsThrown() {
        // query
        String query = "SELECT pg_advisory_unlock(?, ?)";

        // mock behaviors
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).prepareStatement(anyString());

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.advisoryUnlock(connMock, 1));
        String expected = "Failed executing query [%s]".formatted(query);
        assertEquals(expected, e.getMessage());
    }

    @Test
    @SneakyThrows
    void getConnection_WhenException_ThenAnExceptionIsThrown() {
        doThrow(SQLException.class).when(dataSourceMock).getConnection();
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.getConnection());
        assertEquals("Failed getting data-source connection", e.getMessage());
        verify(dataSourceMock, times(1)).getConnection();
    }

    @Test
    @SneakyThrows
    void getConnection_WhenSuccess_ThenTheConnectionIsReturned() {
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(dataSourceMock).getConnection();
        assertEquals(connMock, instance.getConnection());
        verify(dataSourceMock, times(1)).getConnection();
    }

    @Test
    void closeSafely_WhenConnectionIsNull_ThenNothingIsThrown() {
        assertDoesNotThrow(() -> instance.closeSafely(null));
    }

    @Test
    @SneakyThrows
    void closeSafely_WhenExceptionClose_ThenNothingIsThrown() {
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).close();
        assertDoesNotThrow(() -> instance.closeSafely(connMock));
        verify(connMock, times(1)).close();
    }

    @Test
    @SneakyThrows
    void closeSafely_WhenSuccessOnClose_ThenNothingIsThrown() {
        Connection connMock = mock(Connection.class);
        assertDoesNotThrow(() -> instance.closeSafely(connMock));
        verify(connMock, times(1)).close();
    }

    @Test
    void onShutdown_ThenReleaseIsCalled() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), anyInt());
        boolean locked = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertTrue(locked);

        doNothing().when(instanceSpy).release(any());

        // test goal
        instanceSpy.onShutdown();

        // verifications
        verify(instanceSpy, times(1)).release(any());
    }
}
