package ru.tinkoff.kora.bpmn.camunda8.worker.util;

import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class ClasspathResourceUtils {

    private ClasspathResourceUtils() {}

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
