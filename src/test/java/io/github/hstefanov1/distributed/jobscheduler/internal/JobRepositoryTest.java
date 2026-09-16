package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRepositoryTest {

    @Mock
    private EntityManager entityManagerMock;

    @Mock
    private Query queryMock;

    @InjectMocks
    private JobRepository instance;

    @Test
    void claimJobs_WhenNoRows_ThenAnEmptyListIsReturned() {
        mockEntityManagerResult(List.of());

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(PanacheEntityBase::getEntityManager).thenReturn(entityManagerMock);

            List<JobConfig> result = instance.claimJobs(1);
            assertNotNull(result);
            assertTrue(result.isEmpty());

            mock.verify(PanacheEntityBase::getEntityManager, times(1));
        }

        verify(entityManagerMock, times(1)).createNativeQuery(anyString());
        verify(queryMock, times(2)).setParameter(anyString(), any());
        verify(queryMock, times(1)).getResultList();
    }

    @Test
    void claimJobs_WhenRows_ThenListOfJobsIsReturned() {
        mockEntityManagerResult(List.of(1L, 2L));
        JobConfig jobMock1 = mock(JobConfig.class);
        JobConfig jobMock2 = mock(JobConfig.class);

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(PanacheEntityBase::getEntityManager).thenReturn(entityManagerMock);
            mock.when(() -> JobConfig.findByIds(any())).thenReturn(List.of(jobMock1, jobMock2));

            List<JobConfig> result = instance.claimJobs(1);
            assertNotNull(result);
            assertEquals(2, result.size());
            assertSame(jobMock1, result.get(0));
            assertSame(jobMock2, result.get(1));

            mock.verify(PanacheEntityBase::getEntityManager, times(1));
            mock.verify(() -> JobConfig.findByIds(any()), times(1));
        }

        verify(entityManagerMock, times(1)).createNativeQuery(anyString());
        verify(queryMock, times(2)).setParameter(anyString(), any());
        verify(queryMock, times(1)).getResultList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeJob_WhenJobIsNotFound_ThenNothingHappens() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            doReturn(null).when(query).firstResult();

            assertDoesNotThrow(() -> instance.completeJob(1L));

            verify(query, times(1)).firstResult();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeJob_WhenJobIsFound_ThenItIsCompleted() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            JobConfig jobMock = mock(JobConfig.class);
            jobMock.jobName = JobName.EXAMPLE_FAST;
            jobMock.intervalSeconds = 123;
            jobMock.ownerId = "my_owner";
            doReturn(jobMock).when(query).firstResult();

            assertDoesNotThrow(() -> instance.completeJob(1L));
            assertNotNull(jobMock.lastRunAt);
            assertNotNull(jobMock.nextRunAt);
            assertTrue(jobMock.nextRunAt.isAfter(jobMock.lastRunAt));
            assertEquals(123, jobMock.intervalSeconds);
            assertNull(jobMock.ownerId);

            verify(query, times(1)).firstResult();
            verify(jobMock, times(1)).persist();
        }
    }

    private void mockEntityManagerResult(List<Long> ids) {
        when(entityManagerMock.createNativeQuery(anyString())).thenReturn(queryMock);
        when(queryMock.setParameter(anyString(), anyString())).thenReturn(queryMock);
        when(queryMock.setParameter(anyString(), anyInt())).thenReturn(queryMock);
        when(queryMock.getResultList()).thenReturn(ids);
    }
}
