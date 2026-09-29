package io.koraframework.logging.annotation.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingAnnotationProcessorDiscoveryTest {

    @Test
    public void maskingRulesAreGeneratedWithDiscoveredProcessors(@TempDir Path dir) throws IOException {
        var source = dir.resolve("src/test/User.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package test;

            import io.koraframework.logging.common.annotation.Mask;

            @Mask
            public record User(String name, String token) {}
            """);
        var classes = Files.createDirectories(dir.resolve("classes"));
        var generated = Files.createDirectories(dir.resolve("generated"));

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generated));
            var task = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:full"), null, fileManager.getJavaFileObjects(source));

            assertThat(task.call())
                .as("%s", diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList())
                .isTrue();
        }

        assertThat(generated.resolve("test/$User_MaskingRulesModule.java")).exists();
    }
}
