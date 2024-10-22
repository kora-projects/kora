package ru.tinkoff.kora.annotation.processor.common;


import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.compile.ByteArrayJavaFileObject;
import ru.tinkoff.kora.annotation.processor.common.compile.KoraCompileTestJavaFileManager;
import ru.tinkoff.kora.application.graph.*;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
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
        var javaCompiler = ToolProvider.getSystemJavaCompiler();
        var w = new StringWriter();
        var diagnostic = new ArrayList<Diagnostic<? extends JavaFileObject>>();
        var testPackage = testPackage();
        var testClass = this.testInfo.getTestClass().get();
        var testMethod = this.testInfo.getTestMethod().get();
        var commonImports = this.commonImports();
        var sourceList = Arrays.stream(sources).map(s -> "package %s;\n%s\n/**\n* @see %s#%s \n*/\n".formatted(testPackage, commonImports, testClass.getCanonicalName(), testMethod.getName()) + s)
            .map(s -> {
                var prefixes = List.of("class ", "interface ", "@interface ", "record ", "enum ");
                var firstClass = prefixes.stream()
                    .map(p -> Map.entry(s.indexOf(p), p.length()))
                    .filter(e -> e.getKey() >= 0)
                    .map(e -> e.getKey() + e.getValue())
                    .min(Comparator.comparing(Function.identity()))
                    .map(classStart -> {
                        var firstSpace = s.indexOf(" ", classStart + 1);
                        var firstBracket = s.indexOf("(", classStart + 1);
                        var firstSquareBracket = s.indexOf("{", classStart + 1);
                        var classEnd = IntStream.of(firstSpace, firstBracket, firstSquareBracket)
                            .filter(i -> i >= 0)
                            .min()
                            .getAsInt();
                        var className = s.substring(classStart, classEnd).trim();
                        int generic = className.indexOf('<');
                        if(generic == -1) {
                            return className;
                        } else {
                            return className.substring(0, generic);
                        }
                    })
                    .get();

                return new ByteArrayJavaFileObject(JavaFileObject.Kind.SOURCE, testPackage + "." + firstClass, s.getBytes(StandardCharsets.UTF_8));
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
            throw new RuntimeException("Method " + name + " wasn't found");
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
                        if (result instanceof Mono<?> mono) {
                            return (T) mono.block();
                        }
                        if (result instanceof Flux<?> flux) {
                            return (T) flux.blockFirst();
                        }
                        if (result instanceof Publisher<?> mono) {
                            return (T) Mono.from(mono).block();
                        }
                        if (result instanceof Flow.Publisher<?> mono) {
                            return (T) Mono.from(FlowAdapters.toPublisher(mono)).block();
                        }
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
