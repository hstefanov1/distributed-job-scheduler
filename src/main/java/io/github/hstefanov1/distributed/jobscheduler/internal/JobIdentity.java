package io.github.hstefanov1.distributed.jobscheduler.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class JobIdentity {

  public static final String OWNER_ID = resolveOwnerId();

  private static String resolveOwnerId() {
    String podName = System.getenv("HOSTNAME"); // k8s sets this by default
    if (podName != null && !podName.isBlank()) {
      return podName;
    }
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      long pid = ProcessHandle.current().pid();
      return "local-%s-%s".formatted(hostname, pid);
    } catch (UnknownHostException e) {
      return "local-" + UUID.randomUUID();
    }
  }
}
