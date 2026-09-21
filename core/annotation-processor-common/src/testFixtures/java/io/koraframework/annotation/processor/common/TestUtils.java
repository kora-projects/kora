package io.koraframework.annotation.processor.common;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ModuleRef;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class TestUtils {

    public static List<String> classpath;

    public enum ProcessorOptions {

        SUBMODULE_GENERATION("-Akora.app.submodule.enabled=true");

        public final String value;

        ProcessorOptions(String value) {
            this.value = value;
        }
    }

    static {
        var classGraph = new ClassGraph()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan();

        var classpaths = classGraph.getClasspathFiles();
        var modules = classGraph.getModules()
            .stream()
            .filter(Predicate.not(Objects::isNull))
            .map(ModuleRef::getLocationFile);

        classpath = Stream.concat(classpaths.stream(), modules)
            .filter(Objects::nonNull)
            .map(File::toString)
            .distinct()
            .toList();
    }

    public static class CompilationErrorException extends RuntimeException {
        public final List<Diagnostic<? extends JavaFileObject>> diagnostics;

        public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
            return diagnostics;
        }

        public CompilationErrorException(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            super(diagnostics.stream().map(diagnostic -> diagnostic.getMessage(Locale.ENGLISH)).collect(Collectors.joining("\n")));
            this.diagnostics = diagnostics;
        }
    }

    public static CompileResultHolder annotationProcess(Class<?> targetClass, Processor... processors) throws Exception {
        return annotationProcess(List.of(targetClass), processors);
    }

    public static CompileResultHolder annotationProcess(List<Class<?>> targetClasses, Processor... processors) throws Exception {
        var files = targetClasses.stream()
            .map(targetClass -> {
                var targetFile = targetClass.getName().replace('.', '/') + ".java";
                var root = "src/test/java/";
                return Path.of(root + targetFile);
            })
            .toList();

        String compilationId = UUID.randomUUID().toString();
        Path uniqueClassesDir = Path.of("build/in-test-generated/classes-" + compilationId);
        Path uniqueSourcesDir = Path.of("build/in-test-generated/sources-" + compilationId);

        var classLoader = new JavaCompilation()
            .withProcessor(processors)
            .withSources(files)
            .withClassesDir(uniqueClassesDir)
            .withGeneratedSourcesDir(uniqueSourcesDir)
            .compile();

        return new CompileResultHolder(classLoader, uniqueClassesDir, uniqueSourcesDir);
    }

    public record CompileResultHolder(ClassLoader classLoader, Path classesDir, Path sourcesDir) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            try {
                if (classLoader instanceof AutoCloseable ac) {
                    ac.close();
                }
            } finally {
                deleteDirectoryRecursively(classesDir);
                deleteDirectoryRecursively(sourcesDir);
            }
        }

        private void deleteDirectoryRecursively(Path path) throws IOException {
            if (path == null || !Files.exists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        }
                    });
            }
        }
    }
}
