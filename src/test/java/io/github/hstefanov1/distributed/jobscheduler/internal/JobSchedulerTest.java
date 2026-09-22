package io.github.hstefanov1.distributed.jobscheduler.internal;

import io.github.hstefanov1.distributed.jobscheduler.api.JobName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock
    private JobExecutor executorMock;

    @Mock
    private JobRepository repositoryMock;

    @InjectMocks
    private JobScheduler instance;

    @Test
    void dispatchJobs_WhenClaimedTwoJobs_ThenExecutedTwoJobs() {
        JobConfig jobMock1 = mock(JobConfig.class);
        JobConfig jobMock2 = mock(JobConfig.class);
        List<JobConfig> jobMocks = List.of(jobMock1, jobMock2);
        doReturn(jobMocks).when(repositoryMock).claimDueJobs();

        instance.dispatchJobs();

        verify(repositoryMock, times(1)).claimDueJobs();
        verify(executorMock, times(2)).submit(any());
    }

    @Test
    void cleanupOrphanedJobs_ShouldCallCleanup() {
        instance.cleanupOrphanedJobs();
        verify(repositoryMock, times(1)).cleanupOrphanedJobs();
    }

    @Test
    void reportSuspiciousJobs_WhenNoJobs_ThenNothingHappens() {
        doReturn(Set.of()).when(executorMock).getRunning();
        instance.reportSuspiciousJobs();
        verify(executorMock, times(1)).getRunning();
        verify(repositoryMock, times(0)).findSuspiciousJobs(any(), any());
    }

    @Test
    void reportSuspiciousJobs_WhenJobs_ThenAWarningIsLogged() {
        doReturn(Set.of(1L)).when(executorMock).getRunning();
        doReturn(List.of(mock(JobConfig.class))).when(repositoryMock).findSuspiciousJobs(any(), any());
        instance.reportSuspiciousJobs();
        verify(executorMock, times(1)).getRunning();
        verify(repositoryMock, times(1)).findSuspiciousJobs(any(), any());
    }

    @Test
    void reportFailingJobs_WhenNoJobs_ThenNothingHappens() {
        doReturn(Map.of()).when(executorMock).getFailing();
        instance.reportFailingJobs();
        verify(executorMock, times(1)).getFailing();
    }

    @Test
    void reportFailingJobs_WhenJobFailsBelowThreshold_ThenNothingHappens() {
        doReturn(Map.of(mock(JobName.class), 1)).when(executorMock).getFailing();
        instance.reportFailingJobs();
        verify(executorMock, times(1)).getFailing();
    }

    @Test
    void reportFailingJobs_WhenJobFailsAboveThreshold_ThenAnErrorIsLogged() {
        int threshold = JobConstants.THRESHOLD_FAILED_ATTEMPTS;
        int above = threshold + 1;
        doReturn(Map.of(mock(JobName.class), above)).when(executorMock).getFailing();
        instance.reportFailingJobs();
        verify(executorMock, times(1)).getFailing();
    }
}
