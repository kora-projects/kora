package ru.tinkoff.kora.annotation.processor.common;


import ru.tinkoff.kora.annotation.processor.common.compile.KoraCompileTestJavaFileManager;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public record CompileResult(String testPackage, List<Diagnostic<? extends JavaFileObject>> diagnostic, KoraCompileTestJavaFileManager manager) {
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

    public FileObject generatedSourceFile(String className) throws IOException {
        return this.manager.getFileForInput(StandardLocation.SOURCE_OUTPUT, this.testPackage, className);
    }

    public Class<?> loadClass(String className) {
        try {
            return this.manager.getClassLoader(StandardLocation.CLASS_OUTPUT).loadClass(this.testPackage + "." + className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public RuntimeException compilationException() {
        var diagnosticMap = new IdentityHashMap<JavaFileObject, Map<Long, List<Diagnostic<? extends JavaFileObject>>>>();
        for (var d : this.diagnostic) {
            var map = diagnosticMap.computeIfAbsent(d.getSource(), o -> new HashMap<>());
            map.computeIfAbsent(d.getLineNumber(), l -> new ArrayList<>()).add(d);
        }

        try {
            var j = new StringJoiner("\n", "\n", "\n");
            for (var javaFileObject : this.manager.list(StandardLocation.SOURCE_OUTPUT, "", Set.of(JavaFileObject.Kind.SOURCE), true)) {
                var diagnostic = diagnosticMap.getOrDefault(javaFileObject, Map.of());
                j.add(javaFileObject.getName()).add(javaFileToString(javaFileObject, diagnostic));
            }
            for (var javaFileObject : this.manager.list(StandardLocation.SOURCE_PATH, "", Set.of(JavaFileObject.Kind.SOURCE), true)) {
                var diagnostic = diagnosticMap.getOrDefault(javaFileObject, Map.of());
                j.add(javaFileObject.getName()).add(javaFileToString(javaFileObject, diagnostic));
            }

            var errors = this.diagnostic.stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
            throw new RuntimeException("CompilationError: \n" + errors.indent(2) + "\n" + j.toString().indent(2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static String javaFileToString(JavaFileObject object, Map<Long, List<Diagnostic<? extends JavaFileObject>>> diagnostic) throws IOException {
        var j = new StringJoiner("\n", "\n", "\n");
        try (var r = object.openReader(true);
             var sw = new StringWriter()) {
            r.transferTo(sw);
            sw.flush();
            var lines = sw.toString().lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                var lineDiagnostic = diagnostic.getOrDefault((long) i, List.of());
                j.add("%03d | %s".formatted(i, lines.get(i)));
                for (var d : lineDiagnostic) {
                    var diagnosticString = " ".repeat(((int) d.getColumnNumber()) - 1) + "^ " + d.getMessage(Locale.US);
                    j.add(diagnosticString.indent(6));
                }
            }
        }
        return j.toString();
    }
}
