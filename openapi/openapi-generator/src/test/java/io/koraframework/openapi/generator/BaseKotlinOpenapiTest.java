package io.koraframework.openapi.generator;

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider;
import io.koraframework.http.client.symbol.processor.HttpClientSymbolProcessorProvider;
import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider;
import io.koraframework.json.ksp.JsonSymbolProcessorProvider;
import io.koraframework.ksp.common.KotlinCompilation;
import io.koraframework.validation.symbol.processor.ValidSymbolProcessorProvider;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseKotlinOpenapiTest extends BaseOpenapiTest {
    private static final int MATRIX_BATCH_SIZE = 15;
    private static final Map<String, Map<String, Throwable>> MATRIX_FAILURES = new ConcurrentHashMap<>();

    @TempDir
    protected Path kotlinSourcesDir;

    private record GeneratedSpec(String name, Path dir, List<File> files) {}

    /**
     * Checks one spec of the {@link #generateClientParams()} or {@link #generateServerParams()} matrix.
     * A compiler run costs about a second whatever the size of the sources, so the whole matrix of a mode is compiled once,
     * several specs per run, and every spec only reports its own result.
     */
    protected static void processMatrix(SwaggerParams params, String mode) throws Exception {
        if (Arrays.stream(matrixParams(mode)).noneMatch(p -> p.name().equals(params.name()))) {
            throw new IllegalArgumentException(params.name() + " is not a part of the " + mode + " matrix");
        }
        var failure = MATRIX_FAILURES.computeIfAbsent(mode, BaseKotlinOpenapiTest::compileMatrix).get(params.name());
        if (failure instanceof Exception e) {
            throw e;
        }
        if (failure instanceof Error e) {
            throw e;
        }
    }

    private static SwaggerParams[] matrixParams(String mode) {
        return mode.endsWith("-client") ? generateClientParams() : generateServerParams();
    }

    private static Map<String, Throwable> compileMatrix(String mode) {
        var failures = new HashMap<String, Throwable>();
        try {
            var tmp = Files.createTempDirectory("openapi-matrix");
            try {
                var specs = new ArrayList<GeneratedSpec>();
                for (var params : matrixParams(mode)) {
                    try {
                        var dir = Files.createDirectory(tmp.resolve(params.name()));
                        specs.add(new GeneratedSpec(params.name(), dir, generate(dir, params.name(), mode, params.spec(), params.options())));
                    } catch (Throwable e) {
                        failures.put(params.name(), e);
                    }
                }
                for (int from = 0; from < specs.size(); from += MATRIX_BATCH_SIZE) {
                    var batch = specs.subList(from, Math.min(specs.size(), from + MATRIX_BATCH_SIZE));
                    try {
                        compile(mode, batch, Files.createTempDirectory(tmp, "ksp"));
                    } catch (Throwable batchFailure) {
                        // compile the specs of a failed batch one by one to find the ones that do not compile
                        for (var spec : batch) {
                            try {
                                compile(mode, List.of(spec), Files.createTempDirectory(tmp, "ksp"));
                            } catch (Throwable e) {
                                failures.put(spec.name(), e);
                            }
                        }
                    }
                }
            } finally {
                try (var files = Files.walk(tmp)) {
                    files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return failures;
    }

    private static void compile(String mode, List<GeneratedSpec> specs, Path kspSourcesDir) throws Exception {
        var kc = new KotlinCompilation();
        for (var spec : specs) {
            var targetDir = Path.of("build/out").resolve(spec.name()).resolve(mode);
            var sources = kc.getBaseDir().resolve("sources").resolve(spec.name());
            for (var file : spec.files()) {
                var relativized = spec.dir().relativize(file.toPath());
                copy(file.toPath(), targetDir.resolve(relativized));
                var source = sources.resolve(relativized);
                copy(file.toPath(), source);
                if (source.toString().endsWith(".kt")) {
                    kc.withSrc(source);
                }
            }
        }

        kc.withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new HttpClientSymbolProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider()))
            .withGeneratedSourcesDir(kspSourcesDir)
            .compile();

        for (var src : Files.walk(kspSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = kspSourcesDir.relativize(src);
            for (var spec : specs) {
                var packageDir = Path.of("io/koraframework/openapi/generator", spec.name().replace('-', '_'), mode.replace('-', '_'));
                if (specs.size() == 1 || relativized.startsWith(packageDir)) {
                    copy(src, Path.of("build/out").resolve(spec.name()).resolve(mode).resolve(relativized));
                }
            }
        }
    }

    private static void copy(Path src, Path target) throws Exception {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    }

    protected KotlinCompilation process(String name, String mode, String spec, BaseOpenapiTest.SwaggerParams.Options options) throws Exception {
        var files = super.generate(name, mode, spec, options);
        var targetDir = Path.of("build/out").resolve(name).resolve(mode);
        for (var file : files) {
            var src = file.toPath();
            var relativized = openapiSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        }
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

        kc.withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new HttpClientSymbolProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile();


        for (var src : Files.walk(kotlinSourcesDir).filter(Files::isRegularFile).toList()) {
            var relativized = kotlinSourcesDir.relativize(src);
            var target = targetDir.resolve(relativized);
            Files.createDirectories(target.getParent());
            Files.copy(src.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return kc;
    }

    /**
     * Fails on any compiler warning reported against the openapi-generated sources (symbol processor output is not checked).
     */
    protected static void assertNoWarningsInGeneratedSources(KotlinCompilation kc) {
        var warnings = kc.getCompilerMessages().stream().filter(m -> m.contains("warning:") && m.contains("/sources/")).toList();
        assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));
    }
}
