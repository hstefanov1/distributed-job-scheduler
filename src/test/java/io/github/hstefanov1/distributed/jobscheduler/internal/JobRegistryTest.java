package io.github.hstefanov1.distributed.jobscheduler.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobRegistryTest {

  @Mock
  private JobExecutor executorMock;

  @InjectMocks
  private JobRegistry instance;

  @Test
  @SuppressWarnings("unchecked")
  void dispatchJobs_WhenRetrievedTwoJobs_ThenExecutedTwoJobs() {
    JobConfig jobMock1 = mock(JobConfig.class);
    JobConfig jobMock2 = mock(JobConfig.class);
    List<JobConfig> jobMocks = List.of(jobMock1, jobMock2);

    PanacheQuery<JobConfig> queryMock = mock(PanacheQuery.class);
    doReturn(jobMocks).when(queryMock).list();

    try (MockedStatic<PanacheEntityBase> pMock = Mockito.mockStatic(PanacheEntityBase.class)) {

      // mock behaviors
      pMock.when(() -> JobConfig.find(anyString(), any(Instant.class))).thenReturn(queryMock);

      instance.dispatchJobs();

      // static verifications
      pMock.verify(() -> JobConfig.find(anyString(), any(Instant.class)), times(1));
    }

    verify(executorMock, times(1)).execute(jobMock1);
    verify(executorMock, times(1)).execute(jobMock2);
  }
}
