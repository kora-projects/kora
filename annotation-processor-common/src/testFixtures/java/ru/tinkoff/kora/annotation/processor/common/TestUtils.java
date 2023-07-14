package ru.tinkoff.kora.annotation.processor.common;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ModuleRef;
import ru.tinkoff.kora.application.graph.TypeRef;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class TestUtils {
    public static List<String> classpath;

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

    public static ClassLoader testKoraExtension(TypeRef<?> targetClass, TypeRef<?>... requiredDependencies) throws Exception {
        return testKoraExtension(new TypeRef<?>[]{targetClass}, requiredDependencies);
    }

    public static ClassLoader testKoraExtension(TypeRef<?>[] targetClasses, TypeRef<?>... requiredDependencies) throws Exception {
        var template = """
            package test;

            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
                @ru.tinkoff.kora.common.annotation.Root
                default Object someLifecycle(${targets}) {
                    return new Object();
                }
            """;
        for (int i = 0; i < requiredDependencies.length; i++) {
            template += "    default " + requiredDependencies[i].toString() + " component" + i + "() { return null; }\n";
        }
        template += "\n}";
        var sb = new StringBuilder();
        for (int i = 0; i < targetClasses.length; i++) {
            if (i > 0) {
                sb.append(",\n  ");
            }
            sb.append(targetClasses[i].toString()).append(" param").append(i);
        }
        var targets = sb.toString();
        var content = template.replaceAll("\\$\\{targets}", targets);
        var path = Path.of("build/in-test-generated/extension-test-dir/test/TestApp.java");
        Files.deleteIfExists(path);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        var koraAppProcessor = (Processor) Class.forName("ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor").getConstructor().newInstance();
        return annotationProcessFiles(List.of(path.toString()), koraAppProcessor);
    }


    public static ClassLoader annotationProcess(Class<?> targetClass, Processor... processors) throws Exception {
        return annotationProcess(List.of(targetClass), processors);
    }

    public static ClassLoader annotationProcess(List<Class<?>> targetClasses, Processor... processors) throws Exception {
        var files = targetClasses.stream()
            .map(targetClass -> {
                var targetFile = targetClass.getName().replace('.', '/') + ".java";
                var root = "src/test/java/";
                return root + targetFile;
            })
            .toList();
        return annotationProcessFiles(files, processors);
    }

    public static ClassLoader annotationProcess(List<Class<?>> targetClasses, List<Processor> processors) throws Exception {
        var files = targetClasses.stream()
            .map(targetClass -> {
                var targetFile = targetClass.getName().replace('.', '/') + ".java";
                var root = "src/test/java/";
                return root + targetFile;
            })
            .toList();

        return annotationProcessFiles(files, true, processors);
    }

    public static ClassLoader annotationProcessFiles(List<String> targetFiles, Processor... processors) throws Exception {
        return annotationProcessFiles(targetFiles, true, processors);
    }

    public static ClassLoader annotationProcessFiles(List<String> targetFiles, boolean clearClasses, Processor ... processors) throws Exception {
        return annotationProcessFiles(targetFiles, clearClasses, List.of(processors));
    }

    public static ClassLoader annotationProcessFiles(List<String> targetFiles, boolean clearClasses, List<Processor> processors) throws Exception {
        return annotationProcessFiles(targetFiles, List.of(), clearClasses, processors);
    }

    public static ClassLoader annotationProcessFiles(List<String> targetFiles, List<String> targetClasses, boolean clearClasses, List<Processor> processors) throws Exception {
        return annotationProcessFiles(targetFiles, targetClasses, clearClasses, p -> true, processors);
    }

    public static ClassLoader annotationProcessFiles(List<String> targetFiles, List<String> targetClasses, boolean clearClasses, Predicate<Path> clearClassesPredicate, List<Processor> processors) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var out = new StringWriter();
        var diagnostics = new ArrayList<Diagnostic<? extends JavaFileObject>>();
        DiagnosticListener<JavaFileObject> l = d -> {
            diagnostics.add(d);
            System.out.println(d.toString());
        };
        try (var standardFileManager = compiler.getStandardFileManager(l, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            var outClasses = Path.of("build/in-test-generated/classes");
            var sources = Path.of("build/in-test-generated/sources");
            Files.createDirectories(outClasses);
            if (clearClasses) {
                try (var s = Files.walk(outClasses)) {
                    s.forEach(p -> {
                        if (!Files.isDirectory(p) && clearClassesPredicate.test(p)) {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
            }
            Files.createDirectories(sources);
            standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outClasses));
            standardFileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sources));

            var inputSourceFiles = new ArrayList<JavaFileObject>();
            var inputClassFiles = new HashSet<Path>();
            for (var targetFile : targetFiles) {
                var javaObjects = standardFileManager.getJavaFileObjects(targetFile);
                for (var javaObject : javaObjects) {
                    if (javaObject.getKind() == JavaFileObject.Kind.SOURCE) {
                        inputSourceFiles.add(javaObject);
                    } else if (javaObject.getKind() == JavaFileObject.Kind.CLASS) {
                        inputClassFiles.add(Paths.get(targetFile));
                    }
                }
            }
            var cp = new ArrayList<Path>();
            standardFileManager.getLocationAsPaths(StandardLocation.CLASS_PATH).forEach(cp::add);
            cp.add(outClasses);
            standardFileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, cp);

            var task = compiler.getTask(out, standardFileManager, l, List.of("-parameters", "-g", "--enable-preview", "--source", "17", "-XprintRounds"), targetClasses, inputSourceFiles);
            task.setProcessors(processors);
            try {
                task.call();
                if (diagnostics.stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                    return standardFileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
                } else {
                    throw new CompilationErrorException(diagnostics);
                }
            } catch (Exception e) {
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
    }
}
