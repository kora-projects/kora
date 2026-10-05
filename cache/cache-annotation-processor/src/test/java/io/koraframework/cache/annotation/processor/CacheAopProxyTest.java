package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.common.annotation.Component;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheAopProxyTest extends AbstractAnnotationProcessorTest {

    @Test
    public void testCacheWithoutAopIsFinalComponent() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            @io.koraframework.cache.annotation.Cache("my-cache")
            public interface TestCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String> {
            }
            """);
        compileResult.assertSuccess();

        var cache = compileResult.loadClass("$TestCache_Module$Impl");
        assertThat(compileResult.loadClass("TestCache")).isAssignableFrom(cache);
        assertThat(Modifier.isFinal(cache.getModifiers())).isTrue();
        assertThat(cache.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    public void testAopProxyIsGeneratedForCache() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            @io.koraframework.cache.annotation.Cache("my-cache")
            public interface TestCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String> {
                @io.koraframework.logging.common.annotation.Log
                default String describe(String key) { return key; }
            }
            """);
        compileResult.assertSuccess();

        var cache = compileResult.loadClass("$TestCache_Module$Impl");
        assertThat(Modifier.isFinal(cache.getModifiers())).isFalse();
        var proxy = compileResult.loadClass("$TestCache_Module_Impl__AopProxy");
        assertThat(proxy.getSuperclass()).isEqualTo(cache);
        assertThat(proxy.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    public void testAopProxyOfCacheIsUsedInGraph() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor(), new KoraAppProcessor()), """
            @io.koraframework.cache.annotation.Cache("my-cache")
            public interface TestCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String> {
                @io.koraframework.logging.common.annotation.Log
                default String describe(String key) { return key; }
            }
            """, """
            @KoraApp
            public interface TestApp {
              default io.koraframework.config.common.Config config() { return org.mockito.Mockito.mock(io.koraframework.config.common.Config.class); }
              default io.koraframework.config.common.mapper.ConfigValueMapper<io.koraframework.cache.caffeine.CaffeineCacheConfig> mapper() { return org.mockito.Mockito.mock(io.koraframework.config.common.mapper.ConfigValueMapper.class); }
              default io.koraframework.cache.caffeine.CaffeineCacheFactory factory() { return org.mockito.Mockito.mock(io.koraframework.cache.caffeine.CaffeineCacheFactory.class); }
              default io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory telemetry() { return org.mockito.Mockito.mock(io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory.class); }
              default org.slf4j.ILoggerFactory loggerFactory() { return org.slf4j.LoggerFactory.getILoggerFactory(); }

              @Root
              default String root(TestCache cache) { return cache.getClass().getName(); }
            }
            """);
        compileResult.assertSuccess();

        // logger factory is a dependency of the proxy only, so it is in the graph only when the proxy is used
        var graph = loadGraphDraw("TestApp");
        assertThat(graph.getNodes()).hasSize(8);
    }
}
