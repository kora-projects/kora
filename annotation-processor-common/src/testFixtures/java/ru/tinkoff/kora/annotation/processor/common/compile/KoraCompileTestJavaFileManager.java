package ru.tinkoff.kora.annotation.processor.common.compile;


import org.junit.jupiter.api.TestInfo;

import javax.tools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class KoraCompileTestJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> implements JavaFileManager {
    private final Map<Location, List<JavaFileObject>> locationJavaObjects = new HashMap<>();
    private final List<JavaFileObject> sources;
    private final TestInfo testInfo;
    private final KoraCompileTestJavaClassLoader resultClassLoader;

    public KoraCompileTestJavaFileManager(TestInfo testInfo, StandardJavaFileManager delegate, ByteArrayJavaFileObject[] sources) throws IOException {
        super(delegate);
        this.testInfo = testInfo;
        this.sources = List.of(sources);
        for (var source : this.sources) {
            var name = source.getName();
            var path = Paths.get(".", "build", "in-test-generated", "sources")
                .resolve(name.replace('.', '/') + ".java");
            Files.write(path, source.openInputStream().readAllBytes());
        }
        var output = this.locationJavaObjects.computeIfAbsent(StandardLocation.CLASS_OUTPUT, l -> new ArrayList<>());
        this.resultClassLoader = new KoraCompileTestJavaClassLoader(output);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        if (location == StandardLocation.CLASS_OUTPUT) {
            return this.resultClassLoader;
        }
        return super.getClassLoader(location);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        var javaFileObjects = this.locationJavaObjects.computeIfAbsent(location, l -> new ArrayList<>());
        var file = javaFileObjects.stream()
            .filter(f -> f.getName().equals(className))
            .findFirst();
        if (file.isPresent()) {
            return file.get();
        }
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();
        var ext = location == StandardLocation.SOURCE_OUTPUT
            ? ".java"
            : ".class";

        var path = Paths.get(".", "build", "in-test-generated", "sources")
            .resolve(testClass.getPackage().getName().replace('.', '/'))
            .resolve("packageFor" + testClass.getSimpleName())
            .resolve(testMethod.getName())
            .resolve(className.substring(className.lastIndexOf('.') + 1) + ext);
        var f = new InTestGeneratedJavaFileObject(kind, className, path);
        javaFileObjects.add(f);
        return f;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        var javaFileObjects = this.locationJavaObjects.computeIfAbsent(location, l -> new ArrayList<>());
        var file = javaFileObjects.stream()
            .filter(f -> f.getName().equals(className))
            .findFirst();
        if (file.isPresent()) {
            return file.get();
        }
        return super.getJavaFileForInput(location, className, kind);
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        var javaFileObjects = this.locationJavaObjects.computeIfAbsent(location, l -> new ArrayList<>());
        var file = javaFileObjects.stream()
            .filter(f -> f.getName().equals(packageName + "." + relativeName))
            .findFirst();
        return file.orElse(null);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        var files = this.locationJavaObjects.get(location);
        if (files != null) {
            return files;
        }
        if (location == StandardLocation.SOURCE_PATH) {
            if (recurse) {
                return this.sources.stream()
                    .filter(s -> kinds.contains(s.getKind()) && s.getName().startsWith(packageName))
                    .toList();
            }
        }
        return super.list(location, packageName, kinds, recurse);
    }
}
