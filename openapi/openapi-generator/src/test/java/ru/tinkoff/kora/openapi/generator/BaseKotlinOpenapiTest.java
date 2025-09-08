package ru.tinkoff.kora.openapi.generator;

import org.junit.jupiter.api.io.TempDir;
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider;
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientSymbolProcessorProvider;
import ru.tinkoff.kora.http.server.symbol.procesor.HttpControllerProcessorProvider;
import ru.tinkoff.kora.json.ksp.JsonSymbolProcessorProvider;
import ru.tinkoff.kora.ksp.common.KotlinCompilation;
import ru.tinkoff.kora.validation.symbol.processor.ValidSymbolProcessorProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public abstract class BaseKotlinOpenapiTest extends BaseOpenapiTest {
    @TempDir
    protected Path kotlinSourcesDir;

    protected void process(String name, String mode, String spec, BaseOpenapiTest.SwaggerParams.Options options) throws Exception {
        var files = super.generate(name, mode, spec, options);
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var src : Files.walk(kotlinSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = kotlinSourcesDir.relativize(src);
            var target = sources.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            kc.withSrc(target);
        }

        kc.withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new HttpClientSymbolProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile();

        var targetDir = Path.of("build/out").resolve(name).resolve(mode);

        for (var src : Files.walk(kotlinSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = kotlinSourcesDir.relativize(src);
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
