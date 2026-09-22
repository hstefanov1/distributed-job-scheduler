package io.github.hstefanov1.distributed.jobscheduler.internal;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobConstantsTest {

    @Test
    void resolveOwnerId_WhenNoConfigProvider_ThenFallbackToLocalHostPid() {
        InetAddress inetAddressMock = mock(InetAddress.class);
        doReturn("no-provider").when(inetAddressMock).getHostName();

        try (MockedStatic<ConfigProvider> mock1 = mockStatic(ConfigProvider.class);
             MockedStatic<InetAddress> mock2 = Mockito.mockStatic(InetAddress.class)) {
            mock1.when(ConfigProvider::getConfig).thenReturn(null);
            mock2.when(InetAddress::getLocalHost).thenReturn(inetAddressMock);

            String result = JobConstants.resolveOwnerId();
            Pattern pattern = Pattern.compile("^local-no-provider-pid#\\d+");
            assertTrue(pattern.matcher(result).matches());

            mock1.verify(ConfigProvider::getConfig, times(1));
            mock2.verify(InetAddress::getLocalHost, times(1));
        }

        verify(inetAddressMock, times(1)).getHostName();
    }

    @Test
    void resolveOwnerId_WhenHostnameIsNull_ThenFallbackToLocalHostPid() {
        Config configMock = mock(Config.class);
        doReturn(Optional.empty()).when(configMock).getOptionalValue("hostname", String.class);

        InetAddress inetAddressMock = mock(InetAddress.class);
        doReturn("null-hostname").when(inetAddressMock).getHostName();

        try (MockedStatic<ConfigProvider> mock1 = mockStatic(ConfigProvider.class);
             MockedStatic<InetAddress> mock2 = Mockito.mockStatic(InetAddress.class)) {
            mock1.when(ConfigProvider::getConfig).thenReturn(configMock);
            mock2.when(InetAddress::getLocalHost).thenReturn(inetAddressMock);

            String result = JobConstants.resolveOwnerId();
            Pattern pattern = Pattern.compile("^local-null-hostname-pid#\\d+");
            assertTrue(pattern.matcher(result).matches());

            mock1.verify(ConfigProvider::getConfig, times(1));
            mock2.verify(InetAddress::getLocalHost, times(1));
        }

        verify(configMock, times(1)).getOptionalValue("hostname", String.class);
        verify(inetAddressMock, times(1)).getHostName();
    }

    @Test
    void resolveOwnerId_WhenHostnameIsBlank_ThenFallbackToLocalHostPid() {
        Config configMock = mock(Config.class);
        doReturn(Optional.of(" ")).when(configMock).getOptionalValue("hostname", String.class);

        InetAddress inetAddressMock = mock(InetAddress.class);
        doReturn("empty-hostname").when(inetAddressMock).getHostName();

        try (MockedStatic<ConfigProvider> mock1 = mockStatic(ConfigProvider.class);
             MockedStatic<InetAddress> mock2 = Mockito.mockStatic(InetAddress.class)) {
            mock1.when(ConfigProvider::getConfig).thenReturn(configMock);
            mock2.when(InetAddress::getLocalHost).thenReturn(inetAddressMock);

            String result = JobConstants.resolveOwnerId();
            Pattern pattern = Pattern.compile("^local-empty-hostname-pid#\\d+");
            assertTrue(pattern.matcher(result).matches());

            mock1.verify(ConfigProvider::getConfig, times(1));
            mock2.verify(InetAddress::getLocalHost, times(1));
        }

        verify(configMock, times(1)).getOptionalValue("hostname", String.class);
        verify(inetAddressMock, times(1)).getHostName();
    }

    @Test
    void resolveOwnerId_WhenHostnameIsValid_ThenHostnameIsReturned() {
        Config configMock = mock(Config.class);
        doReturn(Optional.of("valid-hostname")).when(configMock).getOptionalValue("hostname", String.class);

        try (MockedStatic<ConfigProvider> mock1 = mockStatic(ConfigProvider.class)) {
            mock1.when(ConfigProvider::getConfig).thenReturn(configMock);
            assertEquals("valid-hostname", JobConstants.resolveOwnerId());
            mock1.verify(ConfigProvider::getConfig, times(1));
        }

        verify(configMock, times(1)).getOptionalValue("hostname", String.class);
    }

    @Test
    void resolveOwnerId_WhenUnknownHostException_ThenFallbackToLocalUnknownPid() {
        try (MockedStatic<InetAddress> mock = Mockito.mockStatic(InetAddress.class)) {
            mock.when(InetAddress::getLocalHost).thenThrow(UnknownHostException.class);
            assertEquals("local-unknown-pid#0", JobConstants.resolveOwnerId());
            mock.verify(InetAddress::getLocalHost, times(2)); // owner_id calls it too
        }
    }

    @Test
    void lockNamespace_ShouldBePositiveNumber() {
        assertTrue(JobConstants.LOCK_NAMESPACE > 0);
    }

    @Test
    void scheduledDelay_ShouldBePositiveNumber() {
        assertEquals(2, JobConstants.SCHEDULED_DELAY);
    }

    @Test
    void maxConcurrentJobs_ShouldBePositiveNumber() {
        assertEquals(10, JobConstants.MAX_CONCURRENT_JOBS);
    }

    @Test
    void maxJobRuntime_ShouldBe30Minutes() {
        assertEquals(30, JobConstants.THRESHOLD_MAX_RUNTIME.toMinutes());
    }

    @Test
    void cleanupOrphanedAfter_ShouldBe4xTimesHigherThanMaxJobRunTime() {
        long maxJobRuntime = JobConstants.THRESHOLD_MAX_RUNTIME.toMinutes();
        long cleanupOrphanedAfter = JobConstants.CLEANUP_ORPHANED_AFTER.toMinutes();
        assertEquals(4 * maxJobRuntime, cleanupOrphanedAfter);
    }
}
