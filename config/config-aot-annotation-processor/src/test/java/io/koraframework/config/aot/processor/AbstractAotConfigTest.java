package io.koraframework.config.aot.processor;

import io.koraframework.annotation.processor.common.*;
import io.koraframework.config.annotation.processor.processor.*;
import io.koraframework.config.aot.AotConfigResolver;
import io.koraframework.validation.annotation.processor.*;
import org.intellij.lang.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public abstract class AbstractAotConfigTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.config.common.annotation.ConfigSource;
            import io.koraframework.config.annotation.processor.*;
            """;
    }

    protected void compileAotConfig(String configFileName, String configContent, @Language("java") String... sources) {
        try {
            var testClass = this.testInfo.getTestClass().get();
            var testMethod = this.testInfo.getTestMethod().get();
            var packagePath = testClass.getPackage().getName().replace('.', '/') + "/packageFor" + testClass.getSimpleName() + "/" + testMethod.getName();

            var classOutputDir = Paths.get(".", "build", "in-test-generated", "classes").resolve(packagePath);
            Files.createDirectories(classOutputDir);
            Files.writeString(classOutputDir.resolve(configFileName), configContent);

            var targetDir = Paths.get(".", "build", "in-test-generated", "sources").resolve(packagePath);
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve(configFileName), configContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.compile(
            List.of(new ConfigParserAnnotationProcessor(), new ConfigSourceAnnotationProcessor(), new ValidAnnotationProcessor(), new AotConfigProcessor()),
            sources
        );
        compileResult.assertSuccess();
    }

    @AfterEach
    void cleanupResource() {
        try {
            var targetClassesDir = Paths.get("build", "in-test-generated", "classes");
            Files.deleteIfExists(targetClassesDir.resolve("application.conf"));
            Files.deleteIfExists(targetClassesDir.resolve("application.yaml"));
            Files.deleteIfExists(targetClassesDir.resolve("application.yml"));
            Files.deleteIfExists(targetClassesDir.resolve("META-INF/kora/config-sources"));

            var targetSourcesDir = Paths.get("build", "in-test-generated", "sources");
            Files.deleteIfExists(targetSourcesDir.resolve("application.conf"));
            Files.deleteIfExists(targetSourcesDir.resolve("application.yaml"));
            Files.deleteIfExists(targetSourcesDir.resolve("application.yml"));
            Files.deleteIfExists(targetSourcesDir.resolve("META-INF/kora/config-sources"));

            AotConfigResolver.clear();
        } catch (IOException ignored) {}
    }
}
