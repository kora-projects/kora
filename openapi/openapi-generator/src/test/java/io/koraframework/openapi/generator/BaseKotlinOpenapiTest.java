package io.koraframework.openapi.generator;

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider;
import io.koraframework.http.client.symbol.processor.HttpClientSymbolProcessorProvider;
import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider;
import io.koraframework.json.ksp.JsonSymbolProcessorProvider;
import io.koraframework.ksp.common.KotlinCompilation;
import io.koraframework.validation.symbol.processor.ValidSymbolProcessorProvider;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

public abstract class BaseKotlinOpenapiTest extends BaseOpenapiTest {
    @TempDir
    protected Path kotlinSourcesDir;

    protected void process(String name, String mode, String spec, BaseOpenapiTest.SwaggerParams.Options options) throws Exception {
        var files = super.generate(name, mode, spec, options);
        var targetDir = Path.of("build", "out", UUID.randomUUID().toString().substring(0, 8))
            .resolve(name)
            .resolve(mode);

        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var src : files) {
            var relativized = openapiSourcesDir.relativize(src.toPath());
            var target = sources.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toPath().toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }

        kc.withProcessors(List.of(
                new JsonSymbolProcessorProvider(),
                new HttpControllerProcessorProvider(),
                new HttpClientSymbolProcessorProvider(),
                new ValidSymbolProcessorProvider(),
                new AopSymbolProcessorProvider()
            ))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile();

        for (var src : Files.walk(kotlinSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = kotlinSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
