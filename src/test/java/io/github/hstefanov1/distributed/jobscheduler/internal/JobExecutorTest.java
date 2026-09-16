package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
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
    private JobFactory factoryMock;

    @Mock
    private JobRepository repositoryMock;

    @InjectMocks
    private JobExecutor instance;

    @BeforeEach
    void setUp() throws Exception {
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
    void submit_WhenSemaphoreAcquired_ThenShouldExecuteJob() throws InterruptedException {
        JobConfig jobMock = mock(JobConfig.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        JobExecutor instanceSpy = spy(instance);
        doNothing().when(instanceSpy).execute(any());

        instanceSpy.submit(jobMock);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        taskCaptor.getValue().run(); // execute the captured runnable synchronously
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, times(1)).release();
        verify(instanceSpy, times(1)).execute(jobMock);
    }

    @Test
    void submit_WhenExecuteThrowsException_ThenShouldStillReleaseSemaphore() throws Exception {
        JobConfig jobMock = mock(JobConfig.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        JobExecutor instanceSpy = spy(instance);
        doThrow(RuntimeException.class).when(instanceSpy).execute(any());

        instanceSpy.submit(jobMock);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        try {
            taskCaptor.getValue().run(); // execute the captured runnable synchronously
        } catch (RuntimeException ignored) {
            // don't care
        }
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, times(1)).release();
        verify(instanceSpy, times(1)).execute(jobMock);
    }

    @Test
    void submit_WhenInterruptedWhileAcquiringSemaphore_ThenShouldNotExecuteJob() throws InterruptedException {
        JobConfig jobMock = mock(JobConfig.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doThrow(InterruptedException.class).when(semaphoreMock).acquire();

        JobExecutor instanceSpy = spy(instance);
        instanceSpy.submit(jobMock);

        verify(executorMock, times(1)).submit(taskCaptor.capture());
        taskCaptor.getValue().run(); // execute the captured runnable synchronously
        verify(semaphoreMock, times(1)).acquire();
        verify(semaphoreMock, never()).release();
        verify(instanceSpy, never()).execute(jobMock);
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
        verify(factoryMock, times(0)).get(any());
        verify(instanceSpy, times(0)).start(any());
    }

    @Test
    void execute_WhenJobIsAlreadyRunning_ThenJobDoesNotStartAgain() throws Exception {
        // mock behaviors
        JobExecutor instanceSpy = spy(instance);
        doNothing().when(instanceSpy).start(any());
        doReturn(true).when(lockMock).tryAcquire(any());

        // build same job twice
        Runnable job1 = createJobRunnable(instanceSpy);
        Runnable job2 = createJobRunnable(instanceSpy);

        // start jobs
        Thread thread1 = Thread.startVirtualThread(job1);
        Thread thread2 = Thread.startVirtualThread(job2);

        // wait them to finish
        thread1.join();
        thread2.join();

        // verifications
        verify(lockMock, times(2)).tryAcquire(any());
        verify(instanceSpy, times(1)).start(any());
    }

    @Test
    void execute_WhenJobIsNotRunning_ThenJobIsStarted() {
        JobConfig job = createJobConfig();
        doReturn(true).when(lockMock).tryAcquire(any());

        JobExecutor instanceSpy = spy(instance);
        doNothing().when(instanceSpy).start(any());
        instanceSpy.execute(job);

        verify(lockMock, times(1)).tryAcquire(any());
        verify(instanceSpy, times(1)).start(any());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void start_WhenJobIsNull_ThenAnExceptionIsThrown() {
        assertThrows(NullPointerException.class, () -> instance.start(null));
    }

    @Test
    void start_WhenJobIsValid_ThenCompleteJobIsCalled() {
        JobExecutor instanceSpy = spy(instance);

        JobConfig job = createJobConfig();

        JobProcessor processorMock = mock(JobProcessor.class);
        doReturn(processorMock).when(factoryMock).get(any());

        JobContext contextMock = mock(JobContext.class);
        doReturn(contextMock).when(instanceSpy).createContext(any());

        doNothing().when(repositoryMock).completeJob(anyLong());

        instanceSpy.start(job);

        verify(factoryMock, times(1)).get(any());
        verify(instanceSpy, times(1)).createContext(any());
        verify(processorMock, times(1)).process(any());
        verify(repositoryMock, times(1)).completeJob(anyLong());
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
        assertEquals(job.ownerId, result.ownerId());
        assertEquals(job.batchSize, result.batchSize());
    }

    @Test
    void onShutdown_WhenTerminatesWithinFirstTimeout_ThenShouldNotForceShutdown() throws InterruptedException {
        doReturn(true).when(executorMock).awaitTermination(anyLong(), any());

        instance.onShutdown();

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(anyLong(), any());
        verify(executorMock, never()).shutdownNow();
    }

    @Test
    void onShutdown_WhenDoesNotTerminateWithinFirstTimeout_ThenShouldCallShutdownNow() throws InterruptedException {
        doReturn(false).when(executorMock).awaitTermination(anyLong(), any());

        instance.onShutdown();

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(2)).awaitTermination(anyLong(), any());
        verify(executorMock, times(1)).shutdownNow();
    }

    @Test
    void onShutdown_WhenTerminatesAfterForcedShutdown_ThenShouldNotLogError() throws InterruptedException {
        doReturn(false).when(executorMock).awaitTermination(1, TimeUnit.MINUTES);
        doReturn(true).when(executorMock).awaitTermination(30, TimeUnit.SECONDS);

        assertDoesNotThrow(() -> instance.onShutdown());

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(2)).awaitTermination(anyLong(), any());
        verify(executorMock, times(1)).shutdownNow();
    }

    @Test
    void onShutdown_WhenInterruptedWhileAwaitingTermination_ThenShouldNotPropagateException() throws InterruptedException {
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

    private Runnable createJobRunnable(JobExecutor executor) {
        return () -> {
            JobConfig jobConfig = createJobConfig();
            executor.execute(jobConfig);
        };
    }
}
