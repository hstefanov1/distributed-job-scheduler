package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobLockTest {

    @Mock
    private DataSource dataSourceMock;

    @InjectMocks
    private JobLock instance;

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testAcquire_WhenNullKey_ThenAnExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.tryAcquire(null));
    }

    @Test
    void testAcquire_WhenAlreadyAcquired_ThenTrueIsReturned() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);

        // mock behaviors
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), any());

        // first call acquires lock, second call reuse the lock
        boolean resultFirst = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        boolean resultSecond = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertTrue(resultFirst);
        assertTrue(resultSecond);

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), any());
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    void testAcquire_WhenExceptionOnTryAdvisoryLock_ThenAnExceptionIsThrown() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);

        // mock behaviors
        doReturn(connMock).when(instanceSpy).getConnection();
        doThrow(IllegalStateException.class).when(instanceSpy).tryAdvisoryLock(any(), any());
        doNothing().when(instanceSpy).closeSafely(any());

        // test goal
        assertThrows(IllegalStateException.class,
                () -> instanceSpy.tryAcquire(JobName.EXAMPLE_FAST));

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), any());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    void testAcquire_WhenLockIsNotAcquired_ThenFalseIsReturned() {
        JobLock instanceSpy = spy(instance);

        // mocks
        Connection connMock = mock(Connection.class);

        // mock behaviors
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(false).when(instanceSpy).tryAdvisoryLock(any(), any());

        // test goal
        boolean result = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertFalse(result);

        // verifications
        verify(instanceSpy, times(1)).getConnection();
        verify(instanceSpy, times(1)).tryAdvisoryLock(any(), any());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void release_WhenNullKey_ThenAnExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.release(null));
    }

    @Test
    void release_WhenLockHeldByAnotherInstance_ThenReleaseIsNotCalled() {
        JobLock instanceSpy = spy(instance);
        instanceSpy.release(JobName.EXAMPLE_FAST);
        verify(instanceSpy, times(0)).advisoryUnlock(any(), any());
        verify(instanceSpy, times(0)).closeSafely(any());
    }

    @Test
    void release_WhenLockHeldByCurrentInstance_ThenReleaseIsCalled() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), any());
        boolean locked = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertTrue(locked);

        // mock behaviors
        doNothing().when(instanceSpy).advisoryUnlock(any(), any());
        doNothing().when(instanceSpy).closeSafely(any());

        // test goal
        instanceSpy.release(JobName.EXAMPLE_FAST);
        verify(instanceSpy, times(1)).advisoryUnlock(any(), any());
        verify(instanceSpy, times(1)).closeSafely(any());
    }

    @Test
    void tryAdvisoryLock_WhenSuccessfullyAcquired_ThenTrueIsReturned() throws Exception {
        // mock behaviors
        Connection connMock = mock(Connection.class);
        PreparedStatement psMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        doReturn(psMock).when(connMock).prepareStatement(anyString());
        doReturn(rsMock).when(psMock).executeQuery();
        doReturn(true).when(rsMock).getBoolean(1);

        // test goal
        boolean result = instance.tryAdvisoryLock(connMock, JobName.EXAMPLE_FAST);
        assertTrue(result);

        // verifications
        verify(connMock, times(1)).prepareStatement(anyString());
        verify(psMock, times(1)).setString(anyInt(), anyString());
        verify(psMock, times(1)).executeQuery();
        verify(rsMock, times(1)).next();
        verify(rsMock, times(1)).getBoolean(anyInt());

        // close
        verify(rsMock, times(1)).close();
        verify(psMock, times(1)).close();
    }

    @Test
    void tryAdvisoryLock_WhenFailedAcquiring_ThenAnExceptionIsThrown() throws Exception {
        // query
        String query = "SELECT pg_try_advisory_lock(hashtext(?))";

        // mock behaviors
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).prepareStatement(anyString());

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.tryAdvisoryLock(connMock, JobName.EXAMPLE_FAST));
        String expected = "Failed executing query [%s]".formatted(query);
        assertEquals(expected, e.getMessage());
    }

    @Test
    void advisoryUnlock_WhenSuccessfullyUnlocked_ThenNoExceptionIsThrown() throws Exception {
        // mock behaviors
        Connection connMock = mock(Connection.class);
        PreparedStatement psMock = mock(PreparedStatement.class);
        doReturn(psMock).when(connMock).prepareStatement(anyString());

        // test goal
        instance.advisoryUnlock(connMock, JobName.EXAMPLE_FAST);

        // verifications
        verify(connMock, times(1)).prepareStatement(anyString());
        verify(psMock, times(1)).setString(anyInt(), anyString());
        verify(psMock, times(1)).execute();

        // close
        verify(psMock, times(1)).close();
    }

    @Test
    void advisoryUnlock_WhenFailedUnlocking_ThenAnExceptionIsThrown() throws Exception {
        // query
        String query = "SELECT pg_advisory_unlock(hashtext(?))";

        // mock behaviors
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).prepareStatement(anyString());

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.advisoryUnlock(connMock, JobName.EXAMPLE_FAST));
        String expected = "Failed executing query [%s]".formatted(query);
        assertEquals(expected, e.getMessage());
    }

    @Test
    void getConnection_WhenException_ThenAnExceptionIsThrown() throws Exception {
        doThrow(SQLException.class).when(dataSourceMock).getConnection();
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                instance.getConnection());
        assertEquals("Failed getting data-source connection", e.getMessage());
        verify(dataSourceMock, times(1)).getConnection();
    }

    @Test
    void getConnection_WhenSuccess_ThenTheConnectionIsReturned() throws Exception {
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
    void closeSafely_WhenExceptionClose_ThenNothingIsThrown() throws SQLException {
        Connection connMock = mock(Connection.class);
        doThrow(SQLException.class).when(connMock).close();
        assertDoesNotThrow(() -> instance.closeSafely(connMock));
        verify(connMock, times(1)).close();
    }

    @Test
    void closeSafely_WhenSuccessOnClose_ThenNothingIsThrown() throws SQLException {
        Connection connMock = mock(Connection.class);
        assertDoesNotThrow(() -> instance.closeSafely(connMock));
        verify(connMock, times(1)).close();
    }

    @Test
    void onShutdown_WhenServiceIsShutdown_ThenReleaseIsCalled() {
        // populate locks
        JobLock instanceSpy = spy(instance);
        Connection connMock = mock(Connection.class);
        doReturn(connMock).when(instanceSpy).getConnection();
        doReturn(true).when(instanceSpy).tryAdvisoryLock(any(), any());
        boolean locked = instanceSpy.tryAcquire(JobName.EXAMPLE_FAST);
        assertTrue(locked);

        doNothing().when(instanceSpy).release(any());

        // test goal
        instanceSpy.onShutdown();

        // verifications
        verify(instanceSpy, times(1)).release(any());
    }
}
