package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.bpmn.camunda8.worker.util.ClasspathResourceUtils;
import ru.tinkoff.kora.bpmn.camunda8.worker.util.Resource;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Camunda8ResourceDeployment implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ZeebeClient client;
    private final Camunda8ClientConfig.DeploymentConfig deploymentConfig;

    public Camunda8ResourceDeployment(ZeebeClient client,
                                      Camunda8ClientConfig.DeploymentConfig deploymentConfig) {
        this.client = client;
        this.deploymentConfig = deploymentConfig;
    }

    @Override
    public void init() throws Exception {
        final List<String> locations = deploymentConfig.resources();
        if (!locations.isEmpty()) {
            final List<Resource> resources = ClasspathResourceUtils.findResources(locations);
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
                    final String decisions = Stream.concat(deploymentEvent.getDecisionRequirements().stream()
                                .map(req -> String.format("Decision:<%s:%d>", req.getDmnDecisionRequirementsId(), req.getVersion())),
                            deploymentEvent.getProcesses().stream()
                                .map(process -> String.format("Process:<%s:%d>", process.getBpmnProcessId(), process.getVersion())))
                        .collect(Collectors.joining(","));
                    logger.info("Resources from locations '{}' deployed: {}", locations, decisions);
                }
            }
        }
    }

    @Override
    public void release() {
        // do nothing
    }
}
