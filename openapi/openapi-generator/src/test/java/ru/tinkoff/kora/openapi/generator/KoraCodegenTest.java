package ru.tinkoff.kora.openapi.generator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer;
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.server.annotation.processor.HttpControllerProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;

import javax.annotation.processing.Processor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.TestUtils.classpath;

class KoraCodegenTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.WARN);
        }
    }

    record SwaggerParams(String mode, String spec, String name) {}

    static SwaggerParams[] generateParams() {
        var result = new ArrayList<SwaggerParams>();
        var modes = new String[]{
            "java_client",
            "java_server",
            "reactive_client",
            "reactive_server",
            "kotlin_client",
            "kotlin_server"
        };
        var files = new String[]{
            "/example/petstoreV3_security_api_key.yaml",
            "/example/petstoreV3_security_basic.yaml",
            "/example/petstoreV3_security_bearer.yaml",
            "/example/petstoreV3_security_oauth.yaml",
            "/example/petstoreV3_discriminator.yaml",
            "/example/petstoreV2.yaml",
            "/example/petstoreV3.yaml",
        };
        for (var fileName : files) {
            for (var mode : modes) {
                var name = fileName.substring(fileName.lastIndexOf('/') + 1)
                    .replace(".yaml", "")
                    .replace(".json", "");
                result.add(new SwaggerParams(mode, fileName, name));
            }
        }
        return result.toArray(SwaggerParams[]::new);
    }

    @ParameterizedTest
    @MethodSource("generateParams")
    void generateTest(SwaggerParams params) throws Exception {
        generate(
            params.name(),
            params.mode(),
            params.spec(),
            "build/out/%s/%s".formatted(params.name(), params.mode().replace('_', '/'))
        );
    }

    private void generate(String name, String mode, String spec, String dir) throws Exception {
        var configurator = new CodegenConfigurator()
            .setGeneratorName("kora")
            .setInputSpec(spec) // or from the server
            .setOutputDir(dir)
            .setApiPackage(dir.replace('/', '.') + ".api")
            .setModelPackage(dir.replace('/', '.') + ".model")
            .addAdditionalProperty("mode", mode)
            .addAdditionalProperty("clientConfigPrefix", "test");
        var processors = new Processor[]{new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new HttpControllerProcessor()};

        var clientOptInput = configurator.toClientOptInput();
        var generator = new DefaultGenerator();

        var files = generator.opts(clientOptInput).generate()
            .stream()
            .map(File::getAbsolutePath)
            .map(String::toString)
            .toList();
        if (mode.contains("kotlin")) {
            compileKotlin(name, files.stream().filter(f -> f.endsWith(".kt")).toList());
        } else {
            TestUtils.annotationProcessFiles(files.stream().filter(f -> f.endsWith(".java")).toList(), processors);
        }
    }


    public static void compileKotlin(String name, List<String> targetFiles) {
        if (targetFiles.isEmpty()) {
            return;
        }
        var k2JvmArgs = new K2JVMCompilerArguments();
        var kotlinOutPath = Path.of("build/in-test-generated").toAbsolutePath().toString();

        k2JvmArgs.setNoReflect(true);
        k2JvmArgs.setNoStdlib(true);
        k2JvmArgs.setNoJdk(false);
        k2JvmArgs.setIncludeRuntime(false);
        k2JvmArgs.setScript(false);
        k2JvmArgs.setDisableStandardScript(true);
        k2JvmArgs.setHelp(false);
        k2JvmArgs.setCompileJava(false);
        k2JvmArgs.setExpression(null);
        k2JvmArgs.setDestination(kotlinOutPath + "/kotlin-classes");
        k2JvmArgs.setJvmTarget("17");
        k2JvmArgs.setJvmDefault("all");
        k2JvmArgs.setFreeArgs(targetFiles);
        k2JvmArgs.setClasspath(String.join(File.pathSeparator, classpath));
        var pluginClassPath = classpath.stream()
            .filter(s -> s.contains("symbol-processing"))
            .toArray(String[]::new);
        var processors = classpath.stream()
            .filter(s -> s.contains("symbol-processor"))
            .collect(Collectors.joining(File.pathSeparator));
        k2JvmArgs.setPluginClasspaths(pluginClassPath);
        var ksp = "plugin:com.google.devtools.ksp.symbol-processing:";
        k2JvmArgs.setPluginOptions(new String[]{
            ksp + "kotlinOutputDir="+ kotlinOutPath,
            ksp + "kspOutputDir=" + kotlinOutPath,
            ksp + "classOutputDir=" + kotlinOutPath,
            ksp + "javaOutputDir=" + kotlinOutPath,
            ksp + "projectBaseDir=" + Path.of(".").toAbsolutePath(),
            ksp + "resourceOutputDir=" + kotlinOutPath,
            ksp + "cachesDir=" + kotlinOutPath,
            ksp + "apclasspath=" + processors,
        });

        var sw = new ByteArrayOutputStream();
        var collector = new PrintingMessageCollector(
            new PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
        );
        var co = new K2JVMCompiler();
        var code = co.exec(collector, Services.EMPTY, k2JvmArgs);
        if (code != ExitCode.OK) {
            throw new RuntimeException(sw.toString(StandardCharsets.UTF_8));
        }
        System.out.println(sw.toString(StandardCharsets.UTF_8));
    }
}
