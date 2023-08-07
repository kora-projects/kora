package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.annotation.processor.common.TestUtils.CompilationErrorException;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableFluxWrongGet;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableWrongFluxPut;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableMonoWrongGetVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableMonoWrongPutVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableWrongPublisherGet;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableWrongPublisherPut;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheAnnotationProcessorTests extends Assertions {

    @Test
    void cacheKeyMultipleAnnotationsOneMethod() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongAnnotationMany.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentMissing() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongArgumentMissing.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongOrder() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongArgumentOrder.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongType() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongArgumentType.class, new AopAnnotationProcessor()));
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
}
