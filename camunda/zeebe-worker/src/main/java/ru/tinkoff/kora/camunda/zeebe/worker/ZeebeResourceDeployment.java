package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.camunda.zeebe.worker.util.ClasspathResourceUtils;
import ru.tinkoff.kora.camunda.zeebe.worker.util.Resource;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ZeebeResourceDeployment implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ZeebeClient client;
    private final ZeebeClientConfig.DeploymentConfig deploymentConfig;

    public ZeebeResourceDeployment(ZeebeClient client,
                                   ZeebeClientConfig.DeploymentConfig deploymentConfig) {
        this.client = client;
        this.deploymentConfig = deploymentConfig;
    }

    @Override
    public void init() throws Exception {
        final List<String> locations = deploymentConfig.resources();
        if (!locations.isEmpty()) {
            logger.debug("Zeebe resources deploying...");
            final long started = System.nanoTime();

            final Set<String> normalizedLocations = locations.stream()
                .map(location -> {
                    if (location.startsWith("classpath:")) {
                        return location;
                    } else {
                        logger.warn("Only locations with `classpath:` prefix are supported, skipping unsupported resource location: {}", location);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            final List<Resource> resources = ClasspathResourceUtils.findResources(normalizedLocations);
            if (!resources.isEmpty()) {
                var deployResourceCommand = client.newDeployResourceCommand();

                DeployResourceCommandStep1.DeployResourceCommandStep2 finalCommand = null;
                for (Resource resource : resources) {
                    try (var is = resource.asInputStream()) {
                        finalCommand = deployResourceCommand.addResourceStream(is, resource.name());
                    }
                }

                if (finalCommand != null) {
                    final DeploymentEvent deploymentEvent = finalCommand.send().get(deploymentConfig.timeout().toMillis(), TimeUnit.MILLISECONDS);
                    final List<String> deployments = Stream.concat(deploymentEvent.getDecisionRequirements().stream()
                                .map(req -> String.format("Decision:<%s:%d>", req.getDmnDecisionRequirementsId(), req.getVersion())),
                            deploymentEvent.getProcesses().stream()
                                .map(process -> String.format("Process:<%s:%d>", process.getBpmnProcessId(), process.getVersion())))
                        .toList();

                    logger.info("Zeebe resources {} deployed in {}", deployments, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                }
            } else {
                logger.debug("Zeebe no resources found for deployment in {}", locations);
            }
        }
    }

    @Override
    public void release() {
        // do nothing
    }
}
