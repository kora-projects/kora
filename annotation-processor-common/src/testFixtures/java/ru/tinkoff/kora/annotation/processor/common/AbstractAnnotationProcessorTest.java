package ru.tinkoff.kora.annotation.processor.common;


import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.compile.ByteArrayJavaFileObject;
import ru.tinkoff.kora.annotation.processor.common.compile.KoraCompileTestJavaFileManager;
import ru.tinkoff.kora.application.graph.*;

import javax.annotation.Nullable;
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
import java.util.function.Supplier;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class AbstractAnnotationProcessorTest {
    protected TestInfo testInfo;
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
        return """
            import ru.tinkoff.kora.common.annotation.*;
            import ru.tinkoff.kora.common.*;
            import javax.annotation.Nullable;
            import java.util.Optional;
            """;
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
                var classStart = s.indexOf("public sealed interface ") + 24;
                if (classStart < 24) {
                    classStart = s.indexOf("public class ") + 13;
                    if (classStart < 13) {
                        classStart = s.indexOf("public final class ") + 19;
                        if (classStart < 19) {
                            classStart = s.indexOf("public interface ") + 17;
                            if (classStart < 17) {
                                classStart = s.indexOf("public @interface ") + 18;
                                if (classStart < 18) {
                                    classStart = s.indexOf("public record ") + 14;
                                    if (classStart < 14) {
                                        classStart = s.indexOf("public enum ") + 12;
                                        if (classStart < 12) {
                                            throw new IllegalArgumentException();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                var firstSpace = s.indexOf(" ", classStart + 1);
                var firstBracket = s.indexOf("(", classStart + 1);
                var firstSquareBracket = s.indexOf("{", classStart + 1);
                var classEnd = Math.min(firstSpace >= 0 ? firstSpace : Integer.MAX_VALUE, Math.min(
                    firstBracket >= 0 ? firstBracket : Integer.MAX_VALUE,
                    firstSquareBracket >= 0 ? firstSquareBracket : Integer.MAX_VALUE
                ));
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

    public Object newObject(String className, Object... params) {
        try {
            var clazz = this.compileResult.loadClass(className);
            return clazz.getConstructors()[0].newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object enumConstant(String className, String name) {
        try {
            var clazz = this.compileResult.loadClass(className);
            assert clazz.isEnum();
            for (var enumConstant : clazz.getEnumConstants()) {
                var e = (Enum<?>) enumConstant;
                if (e.name().equals(name)) {
                    return e;
                }
            }
            throw new RuntimeException("Invalid enum constant: " + name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GeneratedResultCallback<?> newGeneratedObject(String className, Object... params) {
        return () -> newObject(className, params);
    }

    protected interface GeneratedResultCallback<T> {
        T get();
    }


    public GraphContainer loadGraph(String appName) {
        try {
            var type = compileResult.loadClass(appName + "Graph");
            var constructor = type.getConstructors()[0];
            var supplier = (Supplier<ApplicationGraphDraw>) constructor.newInstance();
            var draw = supplier.get();
            return new GraphContainer(draw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class GraphContainer implements Graph {
        private final ApplicationGraphDraw draw;
        private final Graph graph;

        public GraphContainer(ApplicationGraphDraw draw) {
            this.draw = draw;
            this.graph = draw.init().block();
        }

        @Nullable
        public <T> T findByType(Class<? extends T> type) {
            for (var node : draw.getNodes()) {
                var object = graph.get(node);
                if (type.isInstance(object)) {
                    return type.cast(object);
                }
            }
            return null;
        }

        @Nullable
        public <T> List<T> findAllByType(Class<? extends T> type) {
            var result = new ArrayList<T>();
            for (var node : draw.getNodes()) {
                var object = graph.get(node);
                if (type.isInstance(object)) {
                    result.add(type.cast(object));
                }
            }
            return result;
        }

        @Override
        public ApplicationGraphDraw draw() {
            return graph.draw();
        }

        @Override
        public <T> T get(Node<T> node) {
            return graph.get(node);
        }

        @Override
        public <T> ValueOf<T> valueOf(Node<? extends T> node) {
            return graph.valueOf(node);
        }

        @Override
        public <T> PromiseOf<T> promiseOf(Node<T> node) {
            return graph.promiseOf(node);
        }
    }
}
