package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.annotation.processor.common.TestUtils.CompilationErrorException;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.annotation.processor.testcache.DummyCacheTagged;
import io.koraframework.cache.annotation.processor.testdata.sync.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    public void testInnerClassCache() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface OuterType {
              @io.koraframework.cache.annotation.Cache("test")
              interface MyCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String>{}
            }
            """);
        compileResult.assertSuccess();
    }
}
