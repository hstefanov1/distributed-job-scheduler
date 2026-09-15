package io.github.hstefanov1.distributed.jobscheduler.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobValidatorTest {

  @Mock
  private JobFactory factoryMock;

  @InjectMocks
  private JobValidator instance;

  @Test
  void onStart() {
    JobValidator instanceSpy = spy(instance);
    JobConfig job = createJobConfig();
    doReturn(List.of(job)).when(instanceSpy).getJobs();

    instanceSpy.onStart();

    verify(instanceSpy, times(1)).getJobs();
    verify(factoryMock, times(1)).get(any());
  }

  @Test
  void getJobs_WhenException_ThenAnExceptionIsThrown() {
    try (MockedStatic<PanacheEntityBase> mock = Mockito.mockStatic(PanacheEntityBase.class)) {
      mock.when(JobConfig::findAll).thenThrow(RuntimeException.class);

      IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
        instance.getJobs();
      });
      assertEquals("A job_name may refer to a non-existent enum value", e.getMessage());

      mock.verify(PanacheEntityBase::findAll);
    }
  }

  private JobConfig createJobConfig() {
    JobConfig configMock = mock(JobConfig.class);
    configMock.id = 1L;
    configMock.batchSize = 100;
    configMock.intervalSeconds = 300;
    configMock.jobName = JobName.DEACTIVATE_EXPIRED;
    return configMock;
  }
}
