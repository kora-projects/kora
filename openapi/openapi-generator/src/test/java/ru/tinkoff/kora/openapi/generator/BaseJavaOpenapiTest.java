package ru.tinkoff.kora.openapi.generator;

import org.junit.jupiter.api.io.TempDir;
import ru.tinkoff.kora.annotation.processor.common.JavaCompilation;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.server.annotation.processor.HttpControllerProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;
import ru.tinkoff.kora.validation.annotation.processor.ValidAnnotationProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class BaseJavaOpenapiTest extends BaseOpenapiTest {
    @TempDir
    protected Path javaSourcesDir;
    @TempDir
    protected Path javaClasses;


    protected void process(String name, String mode, String spec, BaseOpenapiTest.SwaggerParams.Options options) throws Exception {
        var files = super.generate(name, mode, spec, options);
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

        var targetDir = Path.of("build/out").resolve(name).resolve(mode);

        for (var src : Files.walk(javaSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = javaSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        }
        for (var file : files) {
            var src = file.toPath();
            var relativized = openapiSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
