package ru.tinkoff.kora.annotation.processor.common;


import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public record CompileResult(String testPackage, List<Diagnostic<? extends JavaFileObject>> diagnostic, ClassLoader cl) {
    public boolean isFailed() {
        return this.diagnostic.stream()
            .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
    }

    public void assertSuccess() {
        if (isFailed()) {
            throw compilationException();
        }
    }

    public List<Diagnostic<? extends JavaFileObject>> warnings() {
        return this.diagnostic.stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.WARNING)
            .toList();
    }

    public List<Diagnostic<? extends JavaFileObject>> errors() {
        return this.diagnostic.stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .toList();
    }


    public Class<?> loadClass(String className) {
        try {
            return cl.loadClass(this.testPackage + "." + className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class CompilationFailedException extends RuntimeException {
        public CompilationFailedException(String message) {
            super(message);
        }
    }

    public RuntimeException compilationException() {
        var diagnosticMap = new HashMap<Path, Map<Long, List<Diagnostic<? extends JavaFileObject>>>>();
        for (var d : this.diagnostic) {
            if (d.getSource() != null) {
                var map = diagnosticMap.computeIfAbsent(Path.of(d.getSource().toUri()).toAbsolutePath(), o -> new HashMap<>());
                map.computeIfAbsent(d.getLineNumber(), l -> new ArrayList<>()).add(d);
            }
        }

        try {
            var j = new StringJoiner("\n", "\n", "\n");
            var generatedSources = Files.walk(Path.of("build/in-test-generated/sources")).filter(Files::isRegularFile).toList();
            for (var src : generatedSources) {
                var diagnostic = diagnosticMap.getOrDefault(src.toAbsolutePath(), Map.of());
                if (!diagnostic.isEmpty()) {
                    j.add(src.toString()).add(javaFileToString(src, diagnostic));
                }
            }
            var sources = Files.walk(Paths.get(".", "build", "in-test-generated", "sources")).filter(Files::isRegularFile).toList();
            for (var javaFileObject : sources) {
                var diagnostic = diagnosticMap.getOrDefault(javaFileObject, Map.of());
                if (!diagnostic.isEmpty()) {
                    j.add(javaFileObject.toString()).add(javaFileToString(javaFileObject, diagnostic));
                }
            }

            var errors = this.diagnostic.stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
            throw new CompilationFailedException("CompilationError: \n" + errors.indent(2) + "\n" + j.toString().indent(2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static String javaFileToString(Path object, Map<Long, List<Diagnostic<? extends JavaFileObject>>> diagnostic) throws IOException {
        var j = new StringJoiner("\n", "\n", "\n");
        var lines = Files.readAllLines(object);

        for (int i = 0; i < lines.size(); i++) {
            var lineDiagnostic = diagnostic.getOrDefault((long) i + 1, List.of());
            j.add("%03d | %s".formatted(i, lines.get(i)));
            for (var d : lineDiagnostic) {
                var diagnosticString = " ".repeat(((int) d.getColumnNumber()) - 1) + "^ " + d.getMessage(Locale.US);
                j.add(diagnosticString.indent(6));
            }
        }
        return j.toString();
    }

}
