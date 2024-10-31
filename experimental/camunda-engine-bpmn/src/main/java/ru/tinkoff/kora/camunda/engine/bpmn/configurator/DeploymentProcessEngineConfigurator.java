package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Experimental
public final class DeploymentProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentProcessEngineConfigurator.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final CamundaEngineBpmnConfig engineConfig;

    public DeploymentProcessEngineConfigurator(CamundaEngineBpmnConfig engineConfig) {
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

    private void deployProcessModels(Set<String> normalizedLocations, CamundaEngineBpmnConfig.DeploymentConfig deploymentConfig, RepositoryService repositoryService) throws IOException {
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

    interface Resource {

        String name();

        String path();

        InputStream asInputStream();
    }

    record JarResource(String name, String path, Supplier<InputStream> inputStream) implements Resource {

        @Override
        public InputStream asInputStream() {
            return inputStream.get();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Resource that)) return false;
            return Objects.equals(name, that.name()) && Objects.equals(path, that.path());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, path);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    record FileResource(String name, String path) implements Resource {

        @Override
        public InputStream asInputStream() {
            return FileResource.class.getResourceAsStream("/" + path + "/" + name);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Resource that)) return false;
            return Objects.equals(name, that.name()) && Objects.equals(path, that.path());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, path);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final class ClasspathResourceUtils {

        private ClasspathResourceUtils() {}

        public static Optional<Resource> findResource(String path) {
            return findResources(path).stream().findFirst();
        }

        public static List<Resource> findResources(Collection<String> paths) {
            return paths.stream()
                .distinct()
                .flatMap(p -> findResources(p).stream())
                .distinct()
                .toList();
        }

        /**
         * Scans for all resources under specified path and its subdirectories
         *
         * @param path to start scan from
         * @return list of resources under target package or path
         */
        public static List<Resource> findResources(String path) {
            if (path == null || path.isBlank()) {
                return Collections.emptyList();
            }
            if (path.startsWith("./")) {
                return findResources(path.substring(2));
            }
            if (path.startsWith("/")) {
                return findResources(path.substring(1));
            }

            final Pattern pattern;
            final List<URL> pathResources;
            final String workPath;

            int innerDirectoryFrom = path.lastIndexOf('/');
            if (innerDirectoryFrom == -1) {
                var directoryOnlyResources = getSystemResources(path);
                if (!directoryOnlyResources.isEmpty()) {
                    workPath = path;
                    pathResources = directoryOnlyResources;
                    pattern = null;
                } else {
                    workPath = ".";
                    pathResources = getSystemResources(workPath);
                    pattern = Pattern.compile(path);
                }
            } else {
                workPath = path.substring(0, innerDirectoryFrom);
                pathResources = getSystemResources(workPath);
                pattern = Pattern.compile(path.substring(innerDirectoryFrom + 1));
            }

            return pathResources.stream()
                .map(r -> {
                    if (r.toString().startsWith("jar")) {
                        return loadFromJar(workPath, r, pattern);
                    } else {
                        return loadFromDirectory(workPath, r, pattern);
                    }
                })
                .flatMap(Collection::stream)
                .toList();
        }

        /**
         * Given a package name and a directory returns all classes within that directory
         *
         * @param path to process
         * @return Classes within Directory with package name
         */
        private static List<Resource> loadFromDirectory(String path, URL resource, @Nullable Pattern pattern) {
            final File filePath = new File(resource.getPath());
            if (filePath.isFile()) {
                if (pattern == null) {
                    // mean that path is root self file
                    return List.of(new FileResource(filePath.getName(), "."));
                } else if (pattern.matcher(filePath.getName()).matches()) {
                    return List.of(new FileResource(filePath.getName(), path));
                } else {
                    return List.of();
                }
            } else {
                final List<Resource> resources = new ArrayList<>();
                final String[] files = filePath.list();
                if (files == null || files.length == 0) {
                    return Collections.emptyList();
                }

                for (String fileName : files) {
                    final File file = new File(filePath, fileName);
                    if (file.isFile()) {
                        if (pattern == null) {
                            resources.add(new FileResource(file.getName(), path));
                        } else if (pattern.matcher(fileName).matches()) {
                            resources.add(new FileResource(file.getName(), path));
                        }
                    }
                }
                return resources;
            }
        }

        /**
         * Given a jar file's URL and a package name returns all classes within jar file.
         *
         * @param resource as jar to process
         */
        private static List<Resource> loadFromJar(String path, URL resource, @Nullable Pattern pattern) {
            final String jarPath = resource.getPath()
                .replaceFirst("[.]jar!.*", ".jar")
                .replaceFirst("file:", "");

            try {
                final String jarUrlPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
                try (final JarFile jar = new JarFile(jarUrlPath)) {
                    final List<Resource> classes = new ArrayList<>();
                    final Enumeration<JarEntry> files = jar.entries();
                    while (files.hasMoreElements()) {
                        final JarEntry file = files.nextElement();
                        if (!file.isDirectory()) {
                            if (pattern == null) {
                                classes.add(new JarResource(file.getName(), jarUrlPath, () -> {
                                    try {
                                        return jar.getInputStream(file);
                                    } catch (IOException e) {
                                        throw new IllegalStateException(e);
                                    }
                                }));
                            } else if (pattern.matcher(file.getName()).matches()) {
                                classes.add(new JarResource(file.getName(), jarUrlPath, () -> {
                                    try {
                                        return jar.getInputStream(file);
                                    } catch (IOException e) {
                                        throw new IllegalStateException(e);
                                    }
                                }));
                            }
                        }
                    }

                    return classes;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    "Can't open JAR '" + resource + "', failed with: " + e.getMessage());
            }
        }

        /**
         * Loads system resources as URLs
         *
         * @param path to load resources from
         * @return resources urls
         */
        private static List<URL> getSystemResources(String path) {
            try {
                final Enumeration<URL> resourceUrls = ClasspathResourceUtils.class.getClassLoader().getResources(path);
                if (!resourceUrls.hasMoreElements()) {
                    return Collections.emptyList();
                }

                final List<URL> resources = new ArrayList<>();
                while (resourceUrls.hasMoreElements()) {
                    URL url = resourceUrls.nextElement();
                    resources.add(url);
                }

                return resources;
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
