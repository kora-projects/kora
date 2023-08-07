package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.symbol.processor.testdata.*
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheableGetFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheablePutFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheableGetMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheablePutMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheableGetPublisher
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheablePutPublisher
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess

@KspExperimental
class CacheSymbolProcessorTests {

    @Test
    fun cacheKeyArgumentMissing() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableArgumentMissing::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheKeyArgumentWrongOrder() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableArgumentWrongOrder::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheKeyArgumentWrongType() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableArgumentWrongType::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheNamePatternMismatch() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableNameInvalid::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableGetVoid::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheablePutVoid::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableGetMono::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheablePutMono::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableGetFlux::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheablePutFlux::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableGetPublisher::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheablePutPublisher::class, AopSymbolProcessorProvider()) }
    }
}
