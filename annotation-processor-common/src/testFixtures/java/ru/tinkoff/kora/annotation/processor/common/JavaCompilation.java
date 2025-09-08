package ru.tinkoff.kora.annotation.processor.common;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public class JavaCompilation {
    private static final ThreadLocal<StandardJavaFileManager> MANAGER = ThreadLocal.withInitial(
        () -> ToolProvider.getSystemJavaCompiler().getStandardFileManager(d -> {}, Locale.ENGLISH, StandardCharsets.UTF_8)
    );

    private final List<Path> sourceFiles = new ArrayList<>();
    private final List<Processor> processors = new ArrayList<>();
    private final List<String> processorOptions = new ArrayList<>();
    private final List<Path> classPathEntries = new ArrayList<>();
    private Path compiledClassesDir = Path.of("build/in-test-generated/classes");
    private Path generatedSourcesDir = Path.of("build/in-test-generated/sources");
    private Predicate<Path> clearClassesPredicate = p -> true;

    private final List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

    public List<Diagnostic<? extends JavaFileObject>> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public JavaCompilation withTargetClassesDir(Path path) {
        this.compiledClassesDir = path;
        return this;
    }

    public JavaCompilation withTargetClassesDir(String path) {
        this.compiledClassesDir = Path.of(path);
        return this;
    }

    public JavaCompilation withProcessor(Processor... processor) {
        this.processors.addAll(Arrays.asList(processor));
        return this;
    }

    public JavaCompilation withProcessors(List<? extends Processor> processor) {
        this.processors.addAll(processor);
        return this;
    }

    public JavaCompilation withSources(Path... source) {
        this.sourceFiles.addAll(Arrays.asList(source));
        return this;
    }

    public JavaCompilation withSources(String... source) {
        for (var s : source) {
            this.sourceFiles.add(Path.of(s));
        }
        return this;
    }

    public JavaCompilation withSources(Collection<Path> source) {
        this.sourceFiles.addAll(source);
        return this;
    }

    public JavaCompilation withClassesDir(Path path) {
        this.compiledClassesDir = path;
        return this;
    }

    public JavaCompilation withGeneratedSourcesDir(Path path) {
        this.generatedSourcesDir = path;
        return this;
    }

    public JavaCompilation withGeneratedSourcesDir(String path) {
        this.generatedSourcesDir = Path.of(path);
        return this;
    }

    public JavaCompilation withOption(String opt) {
        this.processorOptions.add(opt);
        return this;
    }

    public JavaCompilation withClassPathEntry(Path path) {
        this.classPathEntries.add(path);
        return this;
    }

    public JavaCompilation withClassPathEntry(String path) {
        this.classPathEntries.add(Path.of(path));
        return this;
    }

    public ClassLoader compile() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var out = new StringWriter();

        Files.createDirectories(compiledClassesDir);
        try (var s = Files.walk(compiledClassesDir)) {
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
        var standardFileManager = MANAGER.get();
        Files.createDirectories(generatedSourcesDir);
        standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(compiledClassesDir));
        standardFileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedSourcesDir));

        var inputSourceFiles = new ArrayList<JavaFileObject>();
        for (var targetFile : sourceFiles) {
            var javaObjects = standardFileManager.getJavaFileObjects(targetFile);
            for (var javaObject : javaObjects) {
                if (javaObject.getKind() == JavaFileObject.Kind.SOURCE) {
                    inputSourceFiles.add(javaObject);
                } else {
                    throw new RuntimeException("Invalid java object type: " + javaObject.getKind());
                }
            }
        }

        if (!this.classPathEntries.isEmpty()) {
            MANAGER.remove();
            var classPath = new ArrayList<File>();
            for (var file : standardFileManager.getLocation(StandardLocation.CLASS_PATH)) {
                classPath.add(file);
            }
            for (var classFile : this.classPathEntries) {
                classPath.add(classFile.toAbsolutePath().toFile());
            }
            standardFileManager.setLocation(StandardLocation.CLASS_PATH, classPath);
        }

        var defaultOptions = new LinkedHashSet<>(List.of("-parameters", "-g", "--enable-preview", "--source", "24", "-XprintRounds"));
        defaultOptions.addAll(processorOptions);
        var task = compiler.getTask(out, standardFileManager, diagnostics::add, defaultOptions, null, inputSourceFiles);
        task.setProcessors(processors);
        try {
            task.call();
            if (diagnostics.stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                for (var classPathEntry : this.classPathEntries) {
                    try (var files = Files.walk(classPathEntry).filter(Files::isRegularFile)) {
                        var it = files.iterator();
                        while (it.hasNext()) {
                            var file = it.next();
                            var finalPath = compiledClassesDir.resolve(classPathEntry.relativize(file));
                            Files.createDirectories(finalPath.getParent());
                            Files.copy(file, finalPath);
                        }
                    }
                }
                return standardFileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
            } else {
                throw new TestUtils.CompilationErrorException(diagnostics);
            }
        } catch (Exception e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
