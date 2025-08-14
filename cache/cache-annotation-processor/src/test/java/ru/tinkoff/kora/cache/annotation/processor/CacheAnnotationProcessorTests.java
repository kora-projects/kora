package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.annotation.processor.common.TestUtils.CompilationErrorException;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheTagged;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableFluxWrongGet;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableWrongFluxPut;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableMonoWrongGetVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableMonoWrongPutVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableWrongPublisherGet;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableWrongPublisherPut;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void cacheTaggedRedisKeyMapper() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyCacheTagged.class, new CacheAnnotationProcessor()));
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
    void cacheGetForMonoVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableMonoWrongGetVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForMonoVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableMonoWrongPutVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheGetForFluxSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableFluxWrongGet.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForFluxSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableWrongFluxPut.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheGetForPublisherSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableWrongPublisherGet.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForPublisherSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableWrongPublisherPut.class, new AopAnnotationProcessor()));
    }

    @Test
    public void testInnerClassCache() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>{}
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    public void testCacheableNoParametersFails() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>{}
            
              class CacheableSync {
            
                 @ru.tinkoff.kora.cache.annotation.Cacheable(OuterType.MyCache.class)
                 public String getValue() { return "1"; }
              }
            }
            """);
        assertTrue(compileResult.isFailed());
    }

    @Test
    public void testCacheableNoParametersNonStringFails() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<Integer, String>{}
            
              class CacheableSync {
            
                 @ru.tinkoff.kora.cache.annotation.Cacheable(OuterType.MyCache.class)
                 public String getValue() { return "1"; }
              }
            }
            """);
        assertTrue(compileResult.isFailed());
    }

    @Test
    public void testCachePutNoParametersFails() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>{}
            
              class CacheableSync {
            
                 @ru.tinkoff.kora.cache.annotation.CachePut(OuterType.MyCache.class)
                 public String getValue() { return "1"; }
              }
            }
            """);
        assertTrue(compileResult.isFailed());
    }

    @Test
    public void testCacheEvictNoParametersFails() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>{}
            
              class CacheableSync {
            
                 @ru.tinkoff.kora.cache.annotation.CacheInvalidate(OuterType.MyCache.class)
                 public String getValue() { return "1"; }
              }
            }
            """);
        assertTrue(compileResult.isFailed());
    }

    @Test
    public void testCacheEvictAllNoParametersSuccess() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            public interface OuterType {
              @ru.tinkoff.kora.cache.annotation.Cache("test")
              interface MyCache extends ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>{}
            
              class CacheableSync {
            
                 @ru.tinkoff.kora.cache.annotation.CacheInvalidate(value = OuterType.MyCache.class, invalidateAll = true)
                 public String getValue() { return "1"; }
              }
            }
            """);
        compileResult.assertSuccess();
    }
}
