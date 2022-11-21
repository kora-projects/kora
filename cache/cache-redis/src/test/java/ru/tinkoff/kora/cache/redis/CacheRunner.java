package ru.tinkoff.kora.cache.redis;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.cache.annotation.processor.CacheKeyAnnotationProcessor;
import ru.tinkoff.kora.cache.redis.testdata.AppWithConfig;
import ru.tinkoff.kora.cache.redis.testdata.Box;
import ru.tinkoff.kora.cache.redis.testdata.CacheableTargetMono;
import ru.tinkoff.kora.cache.redis.testdata.CacheableTargetSync;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigRootAnnotationProcessor;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class CacheRunner extends Assertions {

    public static URI redisUri;

    protected static ApplicationGraphDraw createGraphDraw() throws Exception {
        return createGraphDraw(AppWithConfig.class, CacheableTargetSync.class, CacheableTargetMono.class, Box.class);
    }

    @SuppressWarnings("unchecked")
    protected static ApplicationGraphDraw createGraphDraw(Class<?> app, Class<?>... targetClasses) throws Exception {
        try {
            final List<Class<?>> classes = new ArrayList<>(List.of(targetClasses));
            classes.add(app);
            var classLoader = TestUtils.annotationProcess(classes, new KoraAppProcessor(), new JsonAnnotationProcessor(), new AopAnnotationProcessor(), new CacheKeyAnnotationProcessor(), new ConfigRootAnnotationProcessor(), new ConfigSourceAnnotationProcessor());
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
