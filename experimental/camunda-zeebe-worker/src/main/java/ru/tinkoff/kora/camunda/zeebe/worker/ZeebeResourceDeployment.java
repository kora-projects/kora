package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
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
            final long started = TimeUtils.started();

            final Set<String> normalizedLocations = locations.stream()
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

            final List<Resource> resources = ClasspathResourceUtils.findResources(normalizedLocations);
            logger.info("Paths {} and resources {}", normalizedLocations, resources);
            if (!resources.isEmpty()) {
                var deployResourceCommand = client.newDeployResourceCommand();

                DeployResourceCommandStep1.DeployResourceCommandStep2 finalCommand = null;
                for (Resource resource : resources) {
                    try (var res = resource; var is = res.asInputStream()) {
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

                    logger.info("Zeebe resources {} deployed in {}", deployments, TimeUtils.tookForLogging(started));
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

    interface Resource extends Closeable {

        String name();

        String path();

        InputStream asInputStream();
    }

    record JarResource(String name, String path, Supplier<InputStream> inputStream, @Nullable Closeable closeable) implements Resource {

        public JarResource(String name, String path, Supplier<InputStream> inputStream) {
            this(name, path, inputStream, null);
        }

        @Override
        public InputStream asInputStream() {
            return inputStream.get();
        }

        @Override
        public void close() throws IOException {
            if (closeable != null) {
                closeable.close();
            }
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
        public void close() {

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
                    pattern = Pattern.compile("^" + path);
                }
            } else {
                workPath = path.substring(0, innerDirectoryFrom);
                pathResources = getSystemResources(workPath);
                pattern = Pattern.compile("^" + path.substring(innerDirectoryFrom + 1));
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
                .replaceFirst("[.]tar!.*", ".tar")
                .replaceFirst("file:", "");

            try {
                final String jarUrlPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
                logger.info("JarPath {}, path {}, pattern {}", jarPath, path, pattern);
                final JarFile jar = new JarFile(jarUrlPath);
                final List<Resource> classes = new ArrayList<>();
                final Enumeration<JarEntry> files = jar.entries();
                final AtomicInteger closeCounter = new AtomicInteger(0);
                while (files.hasMoreElements()) {
                    final JarEntry file = files.nextElement();
                    if (!file.isDirectory()) {
                        final String fileFullName = file.getName();
                        final String fileName = (fileFullName.lastIndexOf('/') == -1)
                            ? fileFullName
                            : fileFullName.substring(fileFullName.lastIndexOf('/') + 1);
                        logger.info("File {}, path {}, pattern {}", fileFullName, path, pattern);
                        if ((pattern == null && fileFullName.startsWith(path))
                            || (pattern != null && pattern.matcher(fileName).matches())) {

                            logger.info("Match pattern {}, fileName {}", pattern, fileName);
                            closeCounter.incrementAndGet();
                            classes.add(new JarResource(fileFullName, jarUrlPath, () -> {
                                try {
                                    return jar.getInputStream(file);
                                } catch (Exception e) {
                                    throw new IllegalStateException(e.getMessage() + " for file: " + jarPath, e);
                                }
                            }, () -> {
                                int res = closeCounter.decrementAndGet();
                                if (res < 1) {
                                    jar.close();
                                }
                            }));
                        }
                    }
                }

                return classes;
            } catch (Exception e) {
                throw new IllegalArgumentException("Can't open Jar '" + jarPath, e);
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
