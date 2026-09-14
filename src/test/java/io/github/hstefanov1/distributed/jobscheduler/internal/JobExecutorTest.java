package io.github.hstefanov1.distributed.jobscheduler.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("java:S2925") // we use native
@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

  @Mock
  private JobLock lockMock;

  @Mock
  private JobFactory factoryMock;

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
    verify(instanceSpy, times(0)).reschedule(anyLong());
  }

  @Test
  void execute_WhenJobIsAlreadyRunning_ThenJobDoesNotStartAgain() throws Exception {
    // mock behaviors
    JobExecutor instanceSpy = spy(instance);
    doNothing().when(instanceSpy).reschedule(anyLong());

    JobProcessor processorMock = mock(JobProcessor.class);
    doAnswer(i -> {
      try {
        Thread.sleep(100); // simulate 200ms of work
      } catch (Exception ignored) {
        // we don't care
      }
      return null;
    }).when(processorMock).process(any());
    doReturn(processorMock).when(factoryMock).get(any());
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
    verify(factoryMock, times(1)).get(any());
    verify(processorMock, times(1)).process(any());
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  void reschedule_WhenNullJobId_ThenAnExceptionIsThrown() {
    assertThrows(NullPointerException.class, () -> instance.reschedule(null));
  }

  @Test
  void reschedule_WhenJobIdValid_ThenLastRunAtAndNextRunAtAreUpdated() {
    // mock quarkus transaction calls and call real run
    TransactionRunnerOptions optionsMock = mock(TransactionRunnerOptions.class);
    doAnswer(i -> {
      Runnable runnable = i.getArgument(0);
      runnable.run(); // execute real lambda
      return null;
    }).when(optionsMock).run(any());

    JobConfig jobMock = createJobConfig();

    try (MockedStatic<QuarkusTransaction> tMock = Mockito.mockStatic(QuarkusTransaction.class);
        MockedStatic<PanacheEntityBase> pMock = Mockito.mockStatic(PanacheEntityBase.class)) {

      // mock behaviors
      tMock.when(QuarkusTransaction::requiringNew).thenReturn(optionsMock);
      pMock.when(() -> JobConfig.findById(anyLong())).thenReturn(jobMock);

      assertNull(jobMock.lastRunAt);
      assertNull(jobMock.nextRunAt);
      instance.reschedule(1L);
      assertNotNull(jobMock.lastRunAt);
      assertNotNull(jobMock.nextRunAt);

      // static verifications
      tMock.verify(QuarkusTransaction::requiringNew, times(1));
      pMock.verify(() -> JobConfig.findById(anyLong()), times(1));
    }

    // mock verifications
    verify(optionsMock, times(1)).run(any());
    verify(jobMock, times(1)).persist();
  }

  private JobConfig createJobConfig() {
    JobConfig configMock = mock(JobConfig.class);
    configMock.id = 1L;
    configMock.batchSize = 100;
    configMock.intervalSeconds = 300L;
    configMock.jobName = JobName.DEACTIVATE_EXPIRED;
    return configMock;
  }

  private Runnable createJobRunnable(JobExecutor executor) {
    return () -> {
      JobConfig jobConfig = createJobConfig();
      executor.execute(jobConfig);
    };
  }
}
