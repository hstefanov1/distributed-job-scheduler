package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private Semaphore semaphoreMock;

    @Mock
    private ExecutorService executorMock;

    @Mock
    private JobLock lockMock;

    @Mock
    private JobRegistry registryMock;

    @Mock
    private JobRepository repositoryMock;

    @InjectMocks
    private JobExecutor instance;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        // inject mocked semaphore
        Field field = JobExecutor.class.getDeclaredField("semaphore");
        field.setAccessible(true);
        field.set(instance, semaphoreMock);

        // inject mocked executor service
        field = JobExecutor.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(instance, executorMock);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void submit_WhenJobIsNull_ThenShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> instance.submit(null));
    }

    @Test
    void submit_WhenJobIsAlreadyRunning_ThenJobDoesNotStartAgain() {
        doReturn(mock(Future.class)).when(executorMock).submit(any(Runnable.class));

        JobConfig job = createJobConfig();

        // submit the job twice
        instance.submit(job);
        instance.submit(job);

        // started only once
        verify(executorMock, times(1)).submit(any(Runnable.class));
    }

    @Test
    @SneakyThrows
    void submit_WhenInterruptedWhileAcquiringSemaphore_ThenShouldNotExecuteJob() {
        JobConfig job = createJobConfig();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doThrow(InterruptedException.class).when(semaphoreMock).acquire();

        JobExecutor instanceSpy = spy(instance);
        instanceSpy.submit(job);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        taskCaptor.getValue().run(); // execute the captured runnable synchronously
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, never()).release();
        verify(instanceSpy, never()).execute(job);
    }

    @Test
    @SneakyThrows
    void submit_WhenSemaphoreAcquired_ThenShouldExecuteJob() {
        JobConfig job = createJobConfig();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        JobExecutor instanceSpy = spy(instance);
        doNothing().when(instanceSpy).execute(any());

        instanceSpy.submit(job);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        taskCaptor.getValue().run(); // execute the captured runnable synchronously
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, times(1)).release();
        verify(instanceSpy, times(1)).execute(job);
    }

    @Test
    @SneakyThrows
    void submit_WhenExecuteThrowsException_ThenShouldStillReleaseSemaphore() {
        JobConfig job = createJobConfig();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        JobExecutor instanceSpy = spy(instance);
        doThrow(RuntimeException.class).when(instanceSpy).execute(any());

        instanceSpy.submit(job);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        try {
            taskCaptor.getValue().run(); // execute the captured runnable synchronously
        } catch (RuntimeException ignored) {
            // don't care
        }
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, times(1)).release();
        verify(instanceSpy, times(1)).execute(job);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void execute_WhenJobConfigIsNull_ThenThrowAnException() {
        assertThrows(NullPointerException.class, () -> instance.execute(null));
    }

    @Test
    void execute_WhenLockIsHeldByAnotherInstance_ThenJobIsAborted() {
        // mock behaviors
        JobExecutor instanceSpy = spy(instance);
        doReturn(false).when(lockMock).tryAcquire(any());

        JobConfig job = createJobConfig();
        instanceSpy.execute(job);

        // verifications
        verify(lockMock, times(1)).tryAcquire(any());
        verify(registryMock, times(0)).get(any());
    }

    @Test
    void execute_WhenLockIsAcquired_ThenJobIsStarted() {
        JobConfig job = createJobConfig();
        doReturn(true).when(lockMock).tryAcquire(any());

        JobProcessor processorMock = mock(JobProcessor.class);
        doReturn(processorMock).when(registryMock).get(any());

        JobExecutor instanceSpy = spy(instance);
        instanceSpy.execute(job);

        verify(lockMock, times(1)).tryAcquire(any());
        verify(repositoryMock, times(1)).startJob(anyLong());
        verify(registryMock, times(1)).get(any());
        verify(instanceSpy, times(1)).createContext(any());
        verify(processorMock, times(1)).process(any());
        verify(repositoryMock, times(1)).finishJob(anyLong(), any());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void createContext_WhenJobIsNull_ThenExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.createContext(null));
    }

    @Test
    void createContext_WhenJobIsValid_ThenJobContextIsCreated() {
        JobConfig job = createJobConfig();
        JobContext result = instance.createContext(job);
        assertNotNull(result);
        assertEquals(job.jobName, result.jobName());
        assertEquals(JobConstants.OWNER_ID, result.ownerId());
        assertEquals(job.batchSize, result.batchSize());
    }

    @Test
    void getRunning_ShouldBeUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> instance.getRunning().add(1L));
        assertThrows(UnsupportedOperationException.class, () -> instance.getRunning().clear());
    }

    @Test
    @SneakyThrows
    void onShutdown_WhenTerminatesWithinFirstTimeout_ThenShouldNotForceShutdown() {
        doReturn(true).when(executorMock).awaitTermination(anyLong(), any());

        instance.onShutdown();

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(anyLong(), any());
        verify(executorMock, never()).shutdownNow();
    }

    @Test
    @SneakyThrows
    void onShutdown_WhenDoesNotTerminateWithinFirstTimeout_ThenShouldCallShutdownNow() {
        doReturn(false).when(executorMock).awaitTermination(anyLong(), any());

        instance.onShutdown();

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(2)).awaitTermination(anyLong(), any());
        verify(executorMock, times(1)).shutdownNow();
    }

    @Test
    @SneakyThrows
    void onShutdown_WhenTerminatesAfterForcedShutdown_ThenShouldNotLogError() {
        doReturn(false).when(executorMock).awaitTermination(1, TimeUnit.MINUTES);
        doReturn(true).when(executorMock).awaitTermination(30, TimeUnit.SECONDS);

        assertDoesNotThrow(() -> instance.onShutdown());

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(2)).awaitTermination(anyLong(), any());
        verify(executorMock, times(1)).shutdownNow();
    }

    @Test
    @SneakyThrows
    void onShutdown_WhenInterruptedWhileAwaitingTermination_ThenShouldNotPropagateException() {
        doThrow(InterruptedException.class).when(executorMock).awaitTermination(anyLong(), any());

        assertDoesNotThrow(() -> instance.onShutdown());

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(anyLong(), any());
    }

    private JobConfig createJobConfig() {
        JobConfig configMock = mock(JobConfig.class);
        configMock.id = 1L;
        configMock.batchSize = 100;
        configMock.intervalSeconds = 300;
        configMock.jobName = JobName.EXAMPLE_SLOW;
        return configMock;
    }
}
