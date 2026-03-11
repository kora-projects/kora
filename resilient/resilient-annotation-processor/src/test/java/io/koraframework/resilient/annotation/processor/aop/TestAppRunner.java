package io.koraframework.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Assertions;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.resilient.annotation.processor.aop.testdata.AppWithConfig;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class TestAppRunner extends Assertions {

    public record Pair<T, V>(T first, V second) {}

    public record InitializedGraph(RefreshableGraph refreshableGraph, ApplicationGraphDraw graphDraw) {}

    private record ClassLoaderArguments(List<Class<?>> processors, List<Class<?>> classes) {}

    private static final Map<ClassLoaderArguments, ClassLoader> CLASS_LOADER_MAP = new ConcurrentHashMap<>();

    protected final InitializedGraph getGraph(Class<?> app, Class<?>... targetClasses) {
        try {
            final List<Class<?>> classes = new ArrayList<>(List.of(targetClasses));
            classes.add(app);
            var classLoader = getClassLoader(getProcessors(), classes);
            var clazz = classLoader.loadClass(app.getName() + "Graph");
            var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
            var graphDraw = constructors[0].newInstance().get();
            return new InitializedGraph(graphDraw.init(), graphDraw);
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw new IllegalStateException(e.getCause());
            }

            throw new IllegalStateException(e);
        }
    }

    private ClassLoader getClassLoader(List<AbstractKoraProcessor> processors, List<Class<?>> classes) {
        final List<Class<?>> processorClasses = processors.stream()
            .map(p -> ((Class<?>) p.getClass()))
            .collect(Collectors.toList());

        final ClassLoaderArguments arguments = new ClassLoaderArguments(processorClasses, classes);
        return CLASS_LOADER_MAP.computeIfAbsent(arguments, k -> {
            try {
                return TestUtils.annotationProcess(classes, getProcessors().toArray(AbstractKoraProcessor[]::new));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    protected List<AbstractKoraProcessor> getProcessors() {
        return List.of(
            new KoraAppProcessor(),
            new AopAnnotationProcessor()
        );
    }

    protected final Class<?> getGeneratedClazz(String tClass, Class<?>... classes) {
        final ClassLoader loader = getClassLoader(getProcessors(), List.of(classes));
        try {
            return loader.loadClass(tClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    protected final <T, V> Pair<T, V> getServicesFromGraph(Class<?> app, Class<T> tClass, Class<V> vClass) {
        final InitializedGraph graph = getGraph(app, tClass, vClass);
        final List<?> nodeValues = graph.graphDraw().getNodes().stream()
            .map(n -> graph.refreshableGraph().get(n))
            .toList();

        final T t = nodeValues.stream()
            .filter(v -> tClass.isAssignableFrom(v.getClass()))
            .map(v -> ((T) v))
            .findFirst().orElseThrow();

        final V v = nodeValues.stream()
            .filter(o -> vClass.isAssignableFrom(o.getClass()))
            .map(o -> ((V) o))
            .findFirst().orElseThrow();

        return new Pair<>(t, v);
    }

    protected final <T, V> Pair<T, V> getServicesFromGraph(Class<T> tClass, Class<V> vClass) {
        return getServicesFromGraph(AppWithConfig.class, tClass, vClass);
    }

    protected final <T> T getServiceFromGraph(Class<?> app, Class<T> tClass) {
        return getServicesFromGraph(app, tClass, tClass).first;
    }

    protected final <T> T getServiceFromGraph(Class<T> tClass) {
        return getServiceFromGraph(AppWithConfig.class, tClass);
    }
}
