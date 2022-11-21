package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.annotation.processor.common.TestUtils.CompilationErrorException;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableTargetGetFlux;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux.CacheableTargetPutFlux;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableTargetGetMonoVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableTargetMono;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableTargetPutMonoVoid;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableTargetGetPublisher;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher.CacheableTargetPutPublisher;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.*;

class CacheKeyAnnotationProcessorTests extends Assertions {

    @Test
    void cacheKeyRecordGeneratedForSync() throws Exception {
        var classLoader = TestUtils.annotationProcess(CacheableTargetSync.class, new CacheKeyAnnotationProcessor());
        var record = classLoader.loadClass("ru.tinkoff.kora.cache.annotation.processor.testdata.sync.$CacheKey__sync_cache");

        org.assertj.core.api.Assertions.assertThat(record)
            .isNotNull()
            .hasDeclaredMethods("values")
            .hasOnlyDeclaredFields("arg1", "arg2");
    }

    @Test
    void cacheKeyRecordGeneratedForMono() throws Exception {
        var classLoader = TestUtils.annotationProcess(CacheableTargetMono.class, new CacheKeyAnnotationProcessor());
        var record = classLoader.loadClass("ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.$CacheKey__mono_cache");

        org.assertj.core.api.Assertions.assertThat(record)
            .isNotNull()
            .hasDeclaredMethods("values")
            .hasOnlyDeclaredFields("arg1", "arg2");
    }

    @Test
    void cacheKeyMultipleAnnotationsOneMethod() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetAnnotationMultiple.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentMissing() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetArgumentMissing.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongOrder() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetArgumentWrongOrder.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongType() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetArgumentWrongType.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheNamePatternMismatch() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetNameInvalid.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheGetForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetGetVoid.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cachePutForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetPutVoid.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheGetForMonoVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetGetMonoVoid.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cachePutForMonoVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetPutMonoVoid.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheGetForFluxSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetGetFlux.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cachePutForFluxSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetPutFlux.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cacheGetForPublisherSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetGetPublisher.class, new CacheKeyAnnotationProcessor()));
    }

    @Test
    void cachePutForPublisherSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableTargetPutPublisher.class, new CacheKeyAnnotationProcessor()));
    }
}
