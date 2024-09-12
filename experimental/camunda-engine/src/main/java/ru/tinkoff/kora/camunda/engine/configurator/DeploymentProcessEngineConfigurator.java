package ru.tinkoff.kora.camunda.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DeploymentProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentProcessEngineConfigurator.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final CamundaEngineConfig engineConfig;

    public DeploymentProcessEngineConfigurator(CamundaEngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void setup(ProcessEngine engine) {
        try {
            var deployment = engineConfig.deployment();
            if (deployment != null && !deployment.resources().isEmpty()) {
                final Set<String> normalizedLocations = deployment.resources().stream()
                    .map(location -> {
                        if (location.startsWith("classpath:")) {
                            return location.substring(10);
                        } else {
                            logger.warn("Only locations with `classpath:` prefix are supported, skipping unsupported resource location: {}", location);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                if (deployment.delay() == null) {
                    deployProcessModels(normalizedLocations, deployment, engine.getRepositoryService());
                } else {
                    this.scheduler.schedule(() -> {
                            try {
                                deployProcessModels(normalizedLocations, deployment, engine.getRepositoryService());
                            } catch (IOException e) {
                                logger.error("Camunda Configurator deploying {} resources failed", normalizedLocations, e);
                            }
                        },
                        deployment.delay().toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deployProcessModels(Set<String> normalizedLocations, CamundaEngineConfig.DeploymentConfig deploymentConfig, RepositoryService repositoryService) throws IOException {
        logger.debug("Camunda Configurator deploying {} resources...", normalizedLocations);
        final long started = TimeUtils.started();

        final List<Resource> resources = ClasspathResourceUtils.findResources(normalizedLocations);
        if (resources.isEmpty()) {
            logger.debug("Camunda Configurator found 0 resources");
        } else {
            DeploymentBuilder builder = repositoryService.createDeployment()
                .name(deploymentConfig.name())
                .source(deploymentConfig.name())
                .enableDuplicateFiltering(deploymentConfig.deployChangedOnly());

            if (deploymentConfig.tenantId() != null) {
                builder = builder.tenantId(deploymentConfig.tenantId());
            }

            for (var resource : resources) {
                builder.addInputStream(resource.name(), resource.asInputStream());
            }

            Deployment deployment = builder.deploy();
            logger.info("Camunda Configurator deployed {} resources with deployment id '{}' in {}",
                resources, deployment.getId(), TimeUtils.tookForLogging(started));
        }
    }
}
