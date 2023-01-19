package ru.tinkoff.kora.annotation.processor.common;


import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.compile.ByteArrayJavaFileObject;
import ru.tinkoff.kora.annotation.processor.common.compile.KoraCompileTestJavaFileManager;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class AbstractAnnotationProcessorTest {
    private TestInfo testInfo;
    protected CompileResult compileResult;

    @BeforeEach
    public void beforeEach(TestInfo testInfo) throws IOException {
        this.testInfo = testInfo;
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();

        var path = Paths.get(".", "build", "in-test-generated", "sources")
            .resolve(testClass.getPackage().getName().replace('.', '/'))
            .resolve("packageFor" + testClass.getSimpleName())
            .resolve(testMethod.getName());
        Files.createDirectories(path);
        Files.list(path)
            .filter(Files::isRegularFile)
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    protected final String testPackage() {
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();
        return testClass.getPackageName() + ".packageFor" + testClass.getSimpleName() + "." + testMethod.getName();
    }

    protected String commonImports() {
        return "";
    }

    protected CompileResult compile(List<Processor> processors, @Language("java") String... sources) {
        var javaCompiler = ToolProvider.getSystemJavaCompiler();
        var w = new StringWriter();
        var diagnostic = new ArrayList<Diagnostic<? extends JavaFileObject>>();
        var testPackage = testPackage();
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();
        var commonImports = this.commonImports();
        var sourceList = Arrays.stream(sources).map(s -> "package %s;\n%s\n/**\n* @see %s#%s \n*/\n".formatted(testPackage, commonImports, testClass.getCanonicalName(), testMethod.getName()) + s)
            .map(s -> {
                var classStart = s.indexOf("public class ") + 13;
                if (classStart < 13) {
                    classStart = s.indexOf("public interface ") + 17;
                }
                if (classStart < 17) {
                    classStart = s.indexOf("public record ") + 14;
                }
                if (classStart < 14) {
                    classStart = s.indexOf("public enum ") + 12;
                }
                var classEnd = s.indexOf(" ", classStart + 1);
                var className = s.substring(classStart, classEnd);
                return new ByteArrayJavaFileObject(JavaFileObject.Kind.SOURCE, testPackage + "." + className, s.getBytes(StandardCharsets.UTF_8));
            })
            .toList();
        try (var delegate = javaCompiler.getStandardFileManager(diagnostic::add, Locale.US, StandardCharsets.UTF_8);
             var manager = new KoraCompileTestJavaFileManager(this.testInfo, delegate, sourceList.toArray(ByteArrayJavaFileObject[]::new));) {
            var task = javaCompiler.getTask(
                w,
                manager,
                diagnostic::add,
                List.of("--release", "17"),
                null,
                sourceList
            );
            task.setProcessors(processors);
            task.setLocale(Locale.US);
            task.call();
            return this.compileResult = new CompileResult(testPackage, diagnostic, manager);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
