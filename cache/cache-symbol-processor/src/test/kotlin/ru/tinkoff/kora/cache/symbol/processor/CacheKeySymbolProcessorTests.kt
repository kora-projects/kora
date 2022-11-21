package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.cache.symbol.processor.testdata.*
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheableTargetGetFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheableTargetPutFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheableTargetGetMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheableTargetPutMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheableTargetGetPublisher
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheableTargetPutPublisher
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableTargetSuspend
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess

@KspExperimental
class CacheKeySymbolProcessorTests : Assertions() {

    @Test
    @Throws(Exception::class)
    fun cacheKeyRecordGeneratedForSync() {
        val classLoader = symbolProcess(
            CacheableTargetSync::class,
            CacheKeySymbolProcessorProvider()
        )
        val clazz = classLoader.loadClass("ru.tinkoff.kora.cache.symbol.processor.testdata._CacheKey__sync_cache")
        assertNotNull(clazz)
    }

    @Test
    @Throws(Exception::class)
    fun cacheKeyRecordGeneratedForSuspend() {
        val classLoader = symbolProcess(
            CacheableTargetSuspend::class,
            CacheKeySymbolProcessorProvider()
        )
        val clazz = classLoader.loadClass("ru.tinkoff.kora.cache.symbol.processor.testdata.suspended._CacheKey__suspend_cache")
        assertNotNull(clazz)
    }

    @Test
    fun cacheKeyArgumentMissing() {
        assertThrows(
            CompilationErrorException::class.java
        ) {
            symbolProcess(
                CacheableTargetArgumentMissing::class,
                CacheKeySymbolProcessorProvider()
            )
        }
    }

    @Test
    fun cacheKeyArgumentWrongOrder() {
        assertThrows(
            CompilationErrorException::class.java
        ) {
            symbolProcess(
                CacheableTargetArgumentWrongOrder::class,
                CacheKeySymbolProcessorProvider()
            )
        }
    }

    @Test
    fun cacheKeyArgumentWrongType() {
        assertThrows(
            CompilationErrorException::class.java
        ) {
            symbolProcess(
                CacheableTargetArgumentWrongType::class,
                CacheKeySymbolProcessorProvider()
            )
        }
    }

    @Test
    fun cacheNamePatternMismatch() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetNameInvalid::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetGetVoid::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetPutVoid::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetGetMono::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetPutMono::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetGetFlux::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetPutFlux::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cacheGetForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetGetPublisher::class, CacheKeySymbolProcessorProvider()) }
    }

    @Test
    fun cachePutForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableTargetPutPublisher::class, CacheKeySymbolProcessorProvider()) }
    }
}
