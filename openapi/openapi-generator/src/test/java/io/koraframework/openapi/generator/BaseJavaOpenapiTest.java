package io.koraframework.openapi.generator;

import io.koraframework.annotation.processor.common.JavaCompilation;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.http.client.annotation.processor.HttpClientAnnotationProcessor;
import io.koraframework.http.server.annotation.processor.HttpControllerProcessor;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.validation.annotation.processor.ValidAnnotationProcessor;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class BaseJavaOpenapiTest extends BaseOpenapiTest {
    @TempDir
    protected Path javaSourcesDir;
    @TempDir
    protected Path javaClasses;

    protected void process(String name, String mode, String spec, BaseOpenapiTest.SwaggerParams.Options options) throws Exception {
        var files = super.generate(name, mode, spec, options);

        var targetDir = Path.of("build", "out", UUID.randomUUID().toString().substring(0, 8))
            .resolve(name)
            .resolve(mode);

        var targetFiles = files.stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .toList();
        new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new HttpControllerProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor())
            .withSources(targetFiles)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir)
            .compile();


        for (var src : Files.walk(javaSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = javaSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
