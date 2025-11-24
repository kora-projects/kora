package ru.tinkoff.kora.annotation.processor.common;


import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.application.graph.*;

import javax.annotation.processing.Processor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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
            import jakarta.annotation.Nullable;
            import java.util.Optional;
            """;
    }

    protected CompileResult compile(List<Processor> processors, @Language("java") String... sources) {
        var testPackage = testPackage();
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();
        var commonImports = this.commonImports();
        var sourceList = new ArrayList<Path>();
        for (var source : sources) {
            var string = "package %s;\n%s\n/**\n* @see %s#%s \n*/\n".formatted(testPackage, commonImports, testClass.getCanonicalName(), testMethod.getName()) + source;
            var prefixes = List.of("class ", "interface ", "@interface ", "record ", "enum ");
            var firstClass = prefixes.stream()
                .map(p -> Map.entry(string.indexOf(p), p.length()))
                .filter(e -> e.getKey() >= 0)
                .map(e -> e.getKey() + e.getValue())
                .min(Comparator.comparing(Function.identity()))
                .map(classStart -> {
                    var firstSpace = string.indexOf(" ", classStart + 1);
                    var firstBracket = string.indexOf("(", classStart + 1);
                    var firstSquareBracket = string.indexOf("{", classStart + 1);
                    var classEnd = IntStream.of(firstSpace, firstBracket, firstSquareBracket)
                        .filter(i -> i >= 0)
                        .min()
                        .getAsInt();
                    var className = string.substring(classStart, classEnd).trim();
                    int generic = className.indexOf('<');
                    if (generic == -1) {
                        return className;
                    } else {
                        return className.substring(0, generic);
                    }
                })
                .get();
            var className = testPackage + "." + firstClass;
            var path = Paths.get(".", "build", "in-test-generated", "sources").resolve(className.replace('.', '/') + ".java");
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, string.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sourceList.add(path);
        }

        try {
            var jc = new JavaCompilation()
                .withSources(sourceList)
                .withProcessors(processors);
            var cl = jc.compile();
            return this.compileResult = new CompileResult(testPackage, jc.diagnostics(), cl);
        } catch (TestUtils.CompilationErrorException e) {
            return this.compileResult = new CompileResult(testPackage, e.diagnostics, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object newObject(String className, Object... params) {
        try {
            var clazz = this.compileResult.loadClass(className);
            Constructor<?> declaredConstructor = clazz.getDeclaredConstructors()[0];
            declaredConstructor.setAccessible(true);
            return declaredConstructor.newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object newJavaBean(String className, Object... params) {
        try {
            var clazz = this.compileResult.loadClass(className);
            Constructor<?> declaredConstructor = clazz.getDeclaredConstructors()[0];
            declaredConstructor.setAccessible(true);
            Object o = declaredConstructor.newInstance();

            int i = 0;
            if (params.length > 0) {
                for (Method declaredMethod : clazz.getDeclaredMethods()) {
                    if (declaredMethod.getName().startsWith("set")) {
                        declaredMethod.setAccessible(true);
                        declaredMethod.invoke(o, params[i++]);
                    }
                }
            }

            return o;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object invoke(Object target, String name, Object... params) {
        try {
            for (var method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == params.length) {
                    return method.invoke(target, params);
                }
            }
            throw new IllegalArgumentException("Method " + name + " wasn't found");
        } catch (Exception e) {
            throw (e.getCause() instanceof RuntimeException re)
                ? re
                : new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T invokeAndCast(Object target, String name, Object... params) {
        try {
            for (var method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == params.length) {
                    method.setAccessible(true);
                    var result = method.invoke(target, params);
                    if (result instanceof Future<?> future) {
                        return (T) future.get();
                    }
                    if (result instanceof CompletionStage<?> stage) {
                        return (T) stage.toCompletableFuture().get();
                    }
                    return (T) result;
                }
            }

            throw new IllegalArgumentException("Method " + name + " wasn't found");
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
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

    protected void assertSuccess() {
        compileResult.assertSuccess();
    }

    public Class<?> loadClass(String className) {
        return compileResult.loadClass(className);
    }

    public GraphContainer loadGraph(String appName) {
        try {
            var type = compileResult.loadClass(appName + "Graph");
            var constructor = type.getConstructors()[0];
            @SuppressWarnings("unchecked")
            var supplier = (Supplier<ApplicationGraphDraw>) constructor.newInstance();
            var draw = supplier.get();
            return new GraphContainer(draw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ApplicationGraphDraw loadGraphDraw(String appName) {
        try {
            var type = compileResult.loadClass(appName + "Graph");
            var constructor = type.getConstructors()[0];
            @SuppressWarnings("unchecked")
            var supplier = (Supplier<ApplicationGraphDraw>) constructor.newInstance();
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class GraphContainer implements Graph, AutoCloseable {
        private final ApplicationGraphDraw draw;
        private final RefreshableGraph graph;

        public GraphContainer(ApplicationGraphDraw draw) {
            this.draw = draw;
            this.graph = draw.init();
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

        @Override
        public void close() throws Exception {
            this.graph.release();
        }
    }

    protected static class TestObject {
        public final Class<?> objectClass;
        private final Object object;

        public TestObject(Class<?> objectClass, Object object) {
            this.objectClass = objectClass;
            this.object = object;
        }

        public TestObject(Class<?> objectClass, List<Object> arguments) {
            try {
                var realArgs = new Object[arguments.size()];
                for (int i = 0; i < realArgs.length; i++) {
                    var arg = arguments.get(i);
                    if (arg instanceof GeneratedResultCallback<?> gr) {
                        arg = gr.get();
                    }
                    realArgs[i] = arg;
                }
                this.object = objectClass.getConstructors()[0].newInstance(realArgs);
                this.objectClass = objectClass;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }


        @SuppressWarnings("unchecked")
        public <T> T invoke(String methodName, Object... args) {
            for (var method : objectClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameters().length == args.length) {
                    method.setAccessible(true);
                    try {
                        var result = method.invoke(this.object, args);
                        if (result instanceof Future<?> future) {
                            return (T) future.get();
                        }
                        return (T) result;
                    } catch (InvocationTargetException e) {
                        if (e.getTargetException() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            throw new IllegalArgumentException();
        }
    }
}
