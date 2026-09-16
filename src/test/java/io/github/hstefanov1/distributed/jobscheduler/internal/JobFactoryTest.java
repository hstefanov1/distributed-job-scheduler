package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.github.hstefanov1.distributed.jobscheduler.api.JobProcessor;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class JobFactoryTest {

    @Mock
    private JobProcessor processorMock;

    @Test
    void get_WhenProcessorIsNotRegistered_ThenAnExceptionIsThrown() {
        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.empty()).when(instances).stream();

        JobFactory instance = new JobFactory(instances);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> instance.get(JobName.EXAMPLE_SLOW));
        assertEquals("No job processor registered for job name [EXAMPLE_SLOW]", e.getMessage());
    }

    @Test
    void get_WhenProcessorIsRegistered_ThenIsReturned() {
        JobFactory instance = createInstance();
        JobProcessor result = instance.get(JobName.EXAMPLE_SLOW);
        assertEquals(processorMock, result);
    }

    @Test
    void onStart_WhenExceptionOnFindAllJobs_ThenAnExceptionIsThrown() {
        JobFactory instance = createInstance();
        try (MockedStatic<PanacheEntityBase> mock = Mockito.mockStatic(PanacheEntityBase.class)) {
            mock.when(JobConfig::findAll).thenThrow(RuntimeException.class);

            IllegalStateException e = assertThrows(IllegalStateException.class, instance::onStart);
            assertEquals("A job name may refer to a non-existent enum value", e.getMessage());

            mock.verify(PanacheEntityBase::findAll);
        }
    }

    @Test
    void onStart_WhenFindAllJobsIsCalled_ThenGetIsCalled() {
        JobFactory instanceSpy = spy(createInstance());
        doReturn(null).when(instanceSpy).get(any());

        try (MockedStatic<PanacheEntityBase> mock = Mockito.mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(List.of(mock(JobConfig.class))).when(query).list();
            mock.when(JobConfig::findAll).thenReturn(query);

            instanceSpy.onStart();

            mock.verify(PanacheEntityBase::findAll);
        }

        verify(instanceSpy, times(1)).get(any());
    }

    private JobFactory createInstance() {
        doReturn(JobName.EXAMPLE_SLOW).when(processorMock).name();

        Instance<JobProcessor> instances = mock(Instance.class);
        doReturn(Stream.of(processorMock)).when(instances).stream();

        return new JobFactory(instances);
    }
}
