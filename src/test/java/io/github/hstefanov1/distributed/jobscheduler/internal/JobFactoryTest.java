package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class JobFactoryTest {

    @Test
    void get_WhenProcessorIsNotRegistered_ThenAnExceptionIsThrown() {
        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.empty()).when(instances).stream();

        JobFactory factory = new JobFactory(instances);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> factory.get(JobName.DEACTIVATE_EXPIRED));
        assertEquals("No JobProcessor registered for job [DEACTIVATE_EXPIRED]", e.getMessage());
    }

    @Test
    void get_WhenProcessorIsRegistered_ThenIsReturned() {
        JobProcessor processorMock = mock(JobProcessor.class);
        doReturn(JobName.DEACTIVATE_EXPIRED).when(processorMock).name();

        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.of(processorMock)).when(instances).stream();

        JobFactory factory = new JobFactory(instances);

        JobProcessor result = factory.get(JobName.DEACTIVATE_EXPIRED);
        assertEquals(processorMock, result);
    }
}
