package io.github.hstefanov1.distributed.jobscheduler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Container {

  private static final String PODMAN_CMD = "podman";
  private static final String DOCKER_CMD = "docker";

  /**
   * Checks whether a container runtime is available, and throws if none is found.
   *
   * @throws IllegalStateException if no container runtime is detected while running under the
   *                               {@code local} profile
   */
  public static void run() {
    if (isRuntimeAvailable(PODMAN_CMD)) {
      log.info("Detected compatible container runtime: {}", PODMAN_CMD);
      return;
    }

    if (isRuntimeAvailable(DOCKER_CMD)) {
      log.info("Detected compatible container runtime: {}", DOCKER_CMD);
      return;
    }

    String error = "No container runtime detected";
    String fix = "Start Docker or Podman and try again";
    throw new IllegalStateException("%s. %s.".formatted(error, fix));
  }

  private static boolean isRuntimeAvailable(String command) {
    try {
      Process process = new ProcessBuilder(command, "info")
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException e) {
      return false; // command not found
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
