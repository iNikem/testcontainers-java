package org.testcontainers.containers.wait;

import lombok.extern.slf4j.Slf4j;
import org.rnorth.ducttape.TimeoutException;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.internal.ExternalPortListeningCheck;
import org.testcontainers.containers.wait.internal.InternalCommandPortListeningCheck;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Waits until a socket connection can be established on a port exposed or mapped by the container.
 *
 * @author richardnorth
 */
@Slf4j
public class HostPortWaitStrategy extends GenericContainer.AbstractWaitStrategy {

    @Override
    protected void waitUntilReady() {
        final Set<Integer> externalLivenessCheckPorts = getLivenessCheckPorts();
        if (externalLivenessCheckPorts.isEmpty()) {
            log.debug("Liveness check ports of {} is empty. Not waiting.", container.getContainerName());
            return;
        }

        @SuppressWarnings("unchecked")
        List<Integer> exposedPorts = container.getExposedPorts();

        final Set<Integer> internalPorts = getInternalPorts(externalLivenessCheckPorts, exposedPorts);

        Callable<Boolean> internalCheck = new InternalCommandPortListeningCheck(container, internalPorts);

        Callable<Boolean> externalCheck = new ExternalPortListeningCheck(container, externalLivenessCheckPorts);

        try {
            Unreliables.retryUntilTrue((int) startupTimeout.getSeconds(), TimeUnit.SECONDS, () -> {
                return getRateLimiter().getWhenReady(() -> internalCheck.call() && externalCheck.call());
            });

        } catch (TimeoutException e) {
            throw new ContainerLaunchException("Timed out waiting for container port to open (" +
                    container.getContainerIpAddress() +
                    " ports: " +
                    externalLivenessCheckPorts +
                    " should be listening)");
        }
    }

    private Set<Integer> getInternalPorts(Set<Integer> externalLivenessCheckPorts, List<Integer> exposedPorts) {
        return exposedPorts.stream()
                .filter(it -> externalLivenessCheckPorts.contains(container.getMappedPort(it)))
                .collect(Collectors.toSet());
    }
}
