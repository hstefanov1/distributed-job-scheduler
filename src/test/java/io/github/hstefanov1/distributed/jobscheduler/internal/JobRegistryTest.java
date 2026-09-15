package io.github.hstefanov1.distributed.jobscheduler.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobRegistryTest {

  @Mock
  private JobRepository repositoryMock;

  @Mock
  private JobExecutor executorMock;

  @InjectMocks
  private JobRegistry instance;

  @Test
  void dispatchJobs_WhenRetrievedTwoJobs_ThenExecutedTwoJobs() {
    JobConfig jobMock1 = mock(JobConfig.class);
    JobConfig jobMock2 = mock(JobConfig.class);
    List<JobConfig> jobMocks = List.of(jobMock1, jobMock2);
    doReturn(jobMocks).when(repositoryMock).claimDueJobs(anyInt());

    instance.dispatchJobs();

    verify(repositoryMock, times(1)).claimDueJobs(anyInt());
    verify(executorMock, times(2)).execute(any());
  }
}
