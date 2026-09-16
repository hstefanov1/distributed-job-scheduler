package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobContext;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private JobLock lockMock;

    @Mock
    private JobFactory factoryMock;

    @Mock
    private JobRepository repositoryMock;

    @InjectMocks
    private JobExecutor instance;

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
