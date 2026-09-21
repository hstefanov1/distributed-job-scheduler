package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobValidatorTest {
    @Mock
    private JobRegistry registryMock;

    @InjectMocks
    private JobValidator instance;

    @Test
    void validateJobIds_WhenJobIdIsDuplicated_ThenAnExceptionIsThrown() {
        JobName jobMock1 = mock(JobName.class);
        doReturn(1).when(jobMock1).getId();

        JobName jobMock2 = mock(JobName.class);
        doReturn(1).when(jobMock2).getId(); // same id as jobMock1

        JobName[] jobNames = new JobName[]{jobMock1, jobMock2};
        try (MockedStatic<JobName> mock = mockStatic(JobName.class)) {
            mock.when(JobName::values).thenReturn(jobNames);
            assertThrows(IllegalStateException.class, () -> instance.validateJobIds());
            mock.verify(JobName::values);
        }
    }

    @Test
    void validateJobIds_WhenJobIdsAreUnique_ThenNothingIsThrown() {
        JobName jobMock1 = mock(JobName.class);
        doReturn(1).when(jobMock1).getId();

        JobName jobMock2 = mock(JobName.class);
        doReturn(2).when(jobMock2).getId(); // same id as jobMock1

        JobName[] jobNames = new JobName[]{jobMock1, jobMock2};
        try (MockedStatic<JobName> mock = mockStatic(JobName.class)) {
            mock.when(JobName::values).thenReturn(jobNames);
            assertDoesNotThrow(() -> instance.validateJobIds());
            mock.verify(JobName::values);
        }
    }

    @Test
    void validateJobProcessors_WhenExceptionOnFindAll_ThenAnExceptionIsThrown() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(JobConfig::findAll).thenThrow(RuntimeException.class);

            IllegalStateException e = assertThrows(IllegalStateException.class, instance::onStart);
            String expected = "Failed to load job configs from db. " +
                    "Check connectivity and ensure that every job_name matches a JobName enum.";
            assertEquals(expected, e.getMessage());

            mock.verify(PanacheEntityBase::findAll);
        }

        verify(registryMock, never()).get(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateJobProcessors_WhenFindAllPass_ThenRegistryGetIsCalled() {

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(List.of(mock(JobConfig.class))).when(query).list();
            mock.when(JobConfig::findAll).thenReturn(query);

            assertDoesNotThrow(() -> instance.validateJobProcessors());

            mock.verify(PanacheEntityBase::findAll);
        }

        verify(registryMock, times(1)).get(any());
    }

    @Test
    void onStart_ShouldCallValidators() {
        JobValidator instanceSpy = spy(instance);
        doNothing().when(instanceSpy).validateJobIds();
        doNothing().when(instanceSpy).validateJobProcessors();

        instanceSpy.onStart();

        verify(instanceSpy, times(1)).validateJobIds();
        verify(instanceSpy, times(1)).validateJobProcessors();
    }
}
