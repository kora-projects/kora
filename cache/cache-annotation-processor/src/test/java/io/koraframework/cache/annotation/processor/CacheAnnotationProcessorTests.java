package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.annotation.processor.common.TestUtils.CompilationErrorException;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.annotation.processor.testcache.DummyCacheTagged;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritFinal;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritMediator;
import io.koraframework.cache.annotation.processor.testdata.sync.*;
import io.koraframework.cache.redis.mapper.RedisCacheKeyMapper;
import io.koraframework.cache.redis.mapper.RedisCacheMapperModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheAnnotationProcessorTests extends AbstractAnnotationProcessorTest {

    @Test
    void cacheKeyMultipleAnnotationsOneMethod() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongAnnotationMany.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentMissing() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongArgumentMissing.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyMapper() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncMapper.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheAsyncMode() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableAsync.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheTaggedRedisKeyMapper() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyCacheTagged.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritFinalCacheScanner() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyInheritFinal.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritMediatorCacheScanner() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyInheritMediator.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongOrderMapperRequired() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncWrongArgumentOrder.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongTypeMapperRequired() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncWrongArgumentType.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheNamePatternMismatch() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongName.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheGetForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongGetVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongPutVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    public void testInnerClassCache() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface OuterType {
              @io.koraframework.cache.annotation.Cache("test")
              interface MyCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String>{}
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void redisRecordKeyComponentsWithDelimiterDoNotCollide() throws Exception {
        compile(List.of(new CacheAnnotationProcessor()), """
            public record Key(String a, String b) {}
            """, """
            @io.koraframework.cache.annotation.Cache("cache.k")
            public interface KCache extends io.koraframework.cache.redis.RedisCache<Key, String> {}
            """);
        compileResult.assertSuccess();

        var moduleClass = compileResult.loadClass("$KCache_Module");
        var module = Proxy.newProxyInstance(moduleClass.getClassLoader(), new Class<?>[]{moduleClass},
            (proxy, method, args) -> InvocationHandler.invokeDefault(proxy, method, args));
        var factory = Arrays.stream(moduleClass.getMethods()).filter(m -> m.getName().endsWith("_RedisKeyMapper")).findFirst().orElseThrow();
        var stringMapper = new RedisCacheMapperModule() {}.stringRedisCacheKeyMapper();
        var mapper = (RedisCacheKeyMapper<Object>) factory.invoke(module, stringMapper, stringMapper);

        var key = compileResult.loadClass("Key").getConstructor(String.class, String.class);
        var k1 = mapper.apply(key.newInstance("x:y", "z"));
        var k2 = mapper.apply(key.newInstance("x", "y:z"));

        assertFalse(Arrays.equals(k1, k2), "Key(x:y, z) and Key(x, y:z) must not share a cache key");
        assertArrayEquals("3:x:y:1:z".getBytes(StandardCharsets.UTF_8), k1);
    }
}
