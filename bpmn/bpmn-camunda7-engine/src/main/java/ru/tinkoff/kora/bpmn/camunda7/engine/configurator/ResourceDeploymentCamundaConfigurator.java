package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import ru.tinkoff.kora.bpmn.camunda7.engine.CamundaEngineConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

public final class ResourceDeploymentCamundaConfigurator implements CamundaConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(ResourceDeploymentCamundaConfigurator.class);

    private final PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver();
    private final CamundaEngineConfig engineConfig;

    public ResourceDeploymentCamundaConfigurator(CamundaEngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void setup(ProcessEngine processEngine) {
        try {
            var deployment = engineConfig.deployment();
            if (deployment != null) {
                deployProcessModels(deployment, processEngine.getRepositoryService());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deployProcessModels(CamundaEngineConfig.CamundaDeploymentConfig deploymentConfig, RepositoryService repositoryService) throws IOException {
        final List<String> locations = deploymentConfig.resources();
        logger.info("Searching for models in the resources at configured locations: {}", locations);

        DeploymentBuilder builder = repositoryService.createDeployment()
            .name(deploymentConfig.name())
            .enableDuplicateFiltering(deploymentConfig.deployChangedOnly());

        if (deploymentConfig.tenantId() != null) {
            builder = builder.tenantId(deploymentConfig.tenantId());
        }

        boolean deploy = false;
        for (String extension : Arrays.asList("dmn", "bpmn", "form")) {
            for (String location : locations) {
                String pattern = replacePrefix(location) + "/*." + extension;
                Resource[] models = resourceLoader.getResources(pattern);
                for (Resource model : models) {
                    builder.addInputStream(model.getFilename(), model.getInputStream());
                    logger.info("Deploying model: {}{}", normalizedPath(location), model.getFilename());
                    deploy = true;
                }
            }
        }

        if (deploy) {
            builder.deploy();
        }
    }

    private String replacePrefix(String location) {
        return location.replace("classpath:", ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX);
    }

    private String normalizedPath(String location) {
        if (location.endsWith(".")) {
            location = location.substring(0, location.length() - 1);
        }
        if (!location.endsWith("/") && !location.endsWith("classpath:")) {
            location += "/";
        }
        return location;
    }
}
