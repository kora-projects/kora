package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.annotation.processor.common.TestUtils.CompilationErrorException;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.annotation.processor.testcache.DummyCacheTagged;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritFinal;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritMediator;
import io.koraframework.cache.annotation.processor.testdata.sync.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheAnnotationProcessorTests extends AbstractAnnotationProcessorTest {

    @Test
    void cacheKeyMultipleAnnotationsOneMethod() {
        assertThrows(CompilationErrorException.class, () -> processAndClose(CacheableSyncWrongAnnotationMany.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentMissing() {
        assertThrows(CompilationErrorException.class, () -> processAndClose(CacheableSyncWrongArgumentMissing.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyMapper() {
        assertDoesNotThrow(() -> processAndClose(CacheableSyncMapper.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheAsyncMode() {
        assertDoesNotThrow(() -> processAndClose(CacheableAsync.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheTaggedRedisKeyMapper() {
        assertDoesNotThrow(() -> processAndClose(DummyCacheTagged.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritFinalCacheScanner() {
        assertDoesNotThrow(() -> processAndClose(DummyInheritFinal.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritMediatorCacheScanner() {
        assertDoesNotThrow(() -> processAndClose(DummyInheritMediator.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongOrderMapperRequired() {
        assertDoesNotThrow(() -> processAndClose(CacheableSyncWrongArgumentOrder.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongTypeMapperRequired() {
        assertDoesNotThrow(() -> processAndClose(CacheableSyncWrongArgumentType.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheNamePatternMismatch() {
        assertThrows(CompilationErrorException.class, () -> processAndClose(CacheableSyncWrongName.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheGetForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> processAndClose(CacheableSyncWrongGetVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> processAndClose(CacheableSyncWrongPutVoid.class, new AopAnnotationProcessor()));
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

    private void processAndClose(Class<?> clazz, javax.annotation.processing.Processor processor) throws Exception {
        try (var holder = TestUtils.annotationProcess(clazz, processor)) {}
    }
}
