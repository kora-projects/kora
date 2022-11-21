package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.RefreshableGraph;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigRootAnnotationProcessor;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.AppWithConfig;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.CircuitBreakerFallbackTarget;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.CircuitBreakerTarget;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class CircuitBreakerRunner extends Assertions {

    private static InitializedGraph GRAPH = null;

    public record InitializedGraph(RefreshableGraph refreshableGraph, ApplicationGraphDraw graphDraw) {}

    protected static InitializedGraph createGraphDraw() {
        if (GRAPH == null) {
            GRAPH = createGraphDraw(AppWithConfig.class, CircuitBreakerTarget.class, CircuitBreakerFallbackTarget.class);
        }
        return GRAPH;
    }

    protected static InitializedGraph createGraphDraw(Class<?> app, Class<?>... targetClasses) {
        try {
            final List<Class<?>> classes = new ArrayList<>(List.of(targetClasses));
            classes.add(app);
            var classLoader = TestUtils.annotationProcess(classes, new KoraAppProcessor(), new AopAnnotationProcessor(), new ConfigRootAnnotationProcessor(), new ConfigSourceAnnotationProcessor());
            var clazz = classLoader.loadClass(app.getName() + "Graph");
            var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
            var graphDraw = constructors[0].newInstance().get();
            return new InitializedGraph(graphDraw.init().block(), graphDraw);
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw new IllegalStateException(e.getCause());
            }

            throw new IllegalStateException(e);
        }
    }
}
