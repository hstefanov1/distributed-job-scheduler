package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class JobRepositoryTest {

    @InjectMocks
    private JobRepository instance;

    @Test
    void claimDueJobs_WhenNoRows_ThenAnEmptyListIsReturned() {
        PanacheQuery<JobConfig> queryMock = mock(PanacheQuery.class);
        doReturn(queryMock).when(queryMock).page(any(Page.class));
        doReturn(queryMock).when(queryMock).withLock(any(LockModeType.class));
        doReturn(queryMock).when(queryMock).withHint(anyString(), any());
        doReturn(List.of()).when(queryMock).list();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.find(anyString(), any(Instant.class))).thenReturn(queryMock);

            List<JobConfig> result = instance.claimDueJobs();
            assertEquals(0, result.size());

            verify(queryMock, times(1)).list();
        }
    }

    @Test
    void claimDueJobs_WhenRows_ThenListOfJobsIsReturned() {
        PanacheQuery<JobConfig> queryMock = mock(PanacheQuery.class);
        doReturn(queryMock).when(queryMock).page(any(Page.class));
        doReturn(queryMock).when(queryMock).withLock(any(LockModeType.class));
        doReturn(queryMock).when(queryMock).withHint(anyString(), any());
        doReturn(List.of(mock(JobConfig.class))).when(queryMock).list();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.find(anyString(), any(Instant.class))).thenReturn(queryMock);

            List<JobConfig> result = instance.claimDueJobs();
            assertEquals(1, result.size());

            verify(queryMock, times(1)).list();
        }
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void findSuspiciousJobs_WhenJobIdsNull_ThenShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> instance.findSuspiciousJobs(null));
    }

    @Test
    void findSuspiciousJobs_ShouldReturnSuspiciousJobs() {
        Set<Long> jobIds = Set.of(1L);
        JobConfig jobMock = mock(JobConfig.class);
        List<JobConfig> expectedList = List.of(jobMock);

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.list(
                    eq("id in ?1 and startedAt < ?2"),
                    eq(jobIds),
                    any(Instant.class)
            )).thenReturn(expectedList);

            List<JobConfig> result = instance.findSuspiciousJobs(jobIds);
            assertEquals(1, result.size());
        }
    }

    @Test
    void startJob_WhenJobIsNotFound_ThenAWarningIsLogged() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.findById(anyLong())).thenReturn(null);

            assertDoesNotThrow(() -> instance.startJob(1L));

            mock.verify(() -> JobConfig.findById(anyLong()), times(1));
        }
    }

    @Test
    void startJob_WhenJobIsFound_ThenItIsStarted() {
        JobConfig jobMock = mock(JobConfig.class);
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.findById(anyLong())).thenReturn(jobMock);

            assertNull(jobMock.startedAt);
            assertNull(jobMock.ownerId);
            instance.startJob(1L);
            assertNotNull(jobMock.startedAt);
            assertEquals(JobConstants.OWNER_ID, jobMock.ownerId);

            mock.verify(() -> JobConfig.findById(anyLong()), times(1));
            verify(jobMock, times(1)).persist();
        }
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void finishJob_WhenJobStatusIsNull_ThenShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> instance.finishJob(1L, null, null));
    }

    @Test
    void finishJob_WhenJobIsNotFound_ThenAWarningIsLogged() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(null).when(query).firstResult();
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            assertDoesNotThrow(() -> instance.finishJob(1L, JobStatus.COMPLETED, null));

            verify(query, times(1)).firstResult();
        }
    }

    @Test
    void finishJob_WhenJobIsCompleted_ThenItIsFinished() {
        JobConfig jobMock = mock(JobConfig.class);
        jobMock.intervalSeconds = 123;
        jobMock.ownerId = "my_owner";
        jobMock.startedAt = Instant.now();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(jobMock).when(query).firstResult();
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            assertNull(jobMock.lastRunAt);
            assertNull(jobMock.lastRunStatus);
            assertDoesNotThrow(() -> instance.finishJob(1L, JobStatus.COMPLETED, null));
            assertNotNull(jobMock.lastRunAt);
            assertNull(jobMock.lastRunException);
            assertNotNull(jobMock.nextRunAt);
            assertTrue(jobMock.nextRunAt.isAfter(jobMock.lastRunAt));
            assertEquals(JobStatus.COMPLETED, jobMock.lastRunStatus);
            assertNull(jobMock.startedAt);
            assertNull(jobMock.ownerId);

            verify(query, times(1)).firstResult();
            verify(jobMock, times(1)).persist();
        }
    }

    @Test
    void finishJob_WhenJobIsFailedWithoutThrowable_ThenItIsFinishedAndAWarningIsLogged() {
        JobConfig jobMock = mock(JobConfig.class);
        jobMock.intervalSeconds = 123;
        jobMock.ownerId = "my_owner";
        jobMock.startedAt = Instant.now();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(jobMock).when(query).firstResult();
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            assertNull(jobMock.lastRunStatus);
            assertNull(jobMock.lastRunException);
            assertDoesNotThrow(() -> instance.finishJob(1L, JobStatus.FAILED, null));
            assertEquals(JobStatus.FAILED, jobMock.lastRunStatus);
            assertNull(jobMock.lastRunException);

            verify(query, times(1)).firstResult();
            verify(jobMock, times(1)).persist();
        }
    }

    @Test
    void finishJob_WhenJobIsFailedWithBigThrowable_ThenItIsFinishedAndErrorMessageIsTrimmed() {
        JobConfig jobMock = mock(JobConfig.class);
        jobMock.intervalSeconds = 123;
        jobMock.ownerId = "my_owner";
        jobMock.startedAt = Instant.now();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(jobMock).when(query).firstResult();
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            String bigMessage = "Lorem Ipsum is simply dummy text of the " +
                    "printing and typesetting industry. Lorem Ipsum has been " +
                    "the industry's standard dummy text ever since 1966, when " +
                    "designers at Letraset and James Mosley, the librarian at St " +
                    "Bride Printing Library in London, took a 19";

            assertEquals(256, bigMessage.length());
            assertNull(jobMock.lastRunStatus);
            assertNull(jobMock.lastRunException);
            assertDoesNotThrow(() -> instance.finishJob(1L, JobStatus.FAILED, new RuntimeException(bigMessage)));
            assertEquals(JobStatus.FAILED, jobMock.lastRunStatus);
            assertNotNull(jobMock.lastRunException);
            assertEquals(255, jobMock.lastRunException.length());

            verify(query, times(1)).firstResult();
            verify(jobMock, times(1)).persist();
        }
    }

    @Test
    void finishJob_WhenJobIsFailedWithThrowable_ThenItIsFinishedAndErrorMessageIsSaved() {
        JobConfig jobMock = mock(JobConfig.class);
        jobMock.intervalSeconds = 123;
        jobMock.ownerId = "my_owner";
        jobMock.startedAt = Instant.now();

        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            PanacheQuery<JobConfig> query = mock(PanacheQuery.class);
            doReturn(jobMock).when(query).firstResult();
            mock.when(() -> JobConfig.find(anyString(), anyLong(), anyString())).thenReturn(query);

            assertNull(jobMock.lastRunStatus);
            assertNull(jobMock.lastRunException);
            assertDoesNotThrow(() -> instance.finishJob(1L, JobStatus.FAILED, new RuntimeException("my exception")));
            assertEquals(JobStatus.FAILED, jobMock.lastRunStatus);
            assertNotNull(jobMock.lastRunException);
            assertEquals("my exception", jobMock.lastRunException);

            verify(query, times(1)).firstResult();
            verify(jobMock, times(1)).persist();
        }
    }

    @Test
    void cleanupOrphanedJobs_WhenNoJobs_ThenNothingHappens() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.update(anyString(), any(Instant.class))).thenReturn(0);
            assertDoesNotThrow(() -> instance.cleanupOrphanedJobs());
        }
    }

    @Test
    void cleanupOrphanedJobs_WhenJobsFound_ThenAWarningIsLoggedAndJobsAreUpdated() {
        try (MockedStatic<PanacheEntityBase> mock = mockStatic(PanacheEntityBase.class)) {
            mock.when(() -> JobConfig.update(anyString(), any(Instant.class))).thenReturn(1);
            assertDoesNotThrow(() -> instance.cleanupOrphanedJobs());
        }
    }
}
