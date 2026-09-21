package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class JobRegistryTest {

    @Mock
    private JobProcessor processorMock;

    @Test
    @SuppressWarnings("DataFlowIssue")
    void get_WhenJobNameIsNull_ThenAnExceptionIsThrown() {
        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.empty()).when(instances).stream();

        JobRegistry instance = new JobRegistry(instances);

        assertThrows(NullPointerException.class, () -> instance.get(null));
    }

    @Test
    void get_WhenProcessorIsNotRegistered_ThenAnExceptionIsThrown() {
        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.empty()).when(instances).stream();

        JobRegistry instance = new JobRegistry(instances);

        JobName jobNameMock = mock(JobName.class);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> instance.get(jobNameMock));
        String expected = "No job processor registered for job name [%s]".formatted(jobNameMock);
        assertEquals(expected, e.getMessage());
    }

    @Test
    void get_WhenProcessorIsRegistered_ThenIsReturned() {
        JobName jobNameMock = mock(JobName.class);
        doReturn(jobNameMock).when(processorMock).name();

        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.of(processorMock)).when(instances).stream();

        JobRegistry instance = new JobRegistry(instances);

        JobProcessor result = instance.get(jobNameMock);
        assertEquals(processorMock, result);
    }
}
