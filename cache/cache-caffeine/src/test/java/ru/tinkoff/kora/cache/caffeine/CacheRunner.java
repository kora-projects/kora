package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.cache.annotation.processor.CacheKeyAnnotationProcessor;
import ru.tinkoff.kora.cache.caffeine.testdata.AppWithConfig;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableTargetMono;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableTargetSync;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

abstract class CacheRunner extends Assertions {

    ApplicationGraphDraw createGraphDraw() {
        try {
            return createGraphDraw(AppWithConfig.class, CacheableTargetSync.class, CacheableTargetMono.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    ApplicationGraphDraw createGraphDraw(Class<?> app, Class<?>... targetClasses) throws Exception {
        try {
            final List<Class<?>> classes = new ArrayList<>(List.of(targetClasses));
            classes.add(app);
            var classLoader = TestUtils.annotationProcess(classes, new KoraAppProcessor(), new AopAnnotationProcessor(), new CacheKeyAnnotationProcessor(), new ConfigSourceAnnotationProcessor());
            var clazz = classLoader.loadClass(app.getName() + "Graph");
            var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
            return constructors[0].newInstance().get();
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }
}
