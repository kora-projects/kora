package ru.tinkoff.kora.cache.symbol.processor

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheTagged
import ru.tinkoff.kora.cache.symbol.processor.testdata.*
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheableGetFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux.CacheablePutFlux
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheableGetMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono.CacheablePutMono
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheableGetPublisher
import ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.publisher.CacheablePutPublisher
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess

class CacheSymbolProcessorTests : AbstractSymbolProcessorTest() {

    @Test
    fun cacheKeyArgumentMissing() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(CacheableArgumentMissing::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheKeyMapper() {
        assertDoesNotThrow { symbolProcess(CacheableMapper::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheRedisKeyMapperTagged() {
        assertDoesNotThrow { symbolProcess(DummyCacheTagged::class, CacheSymbolProcessorProvider()) }
    }

    @Test
    fun cacheKeyArgumentWrongOrderMapper() {
        assertDoesNotThrow { symbolProcess(CacheableArgumentWrongOrderMapper::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun cacheKeyArgumentWrongTypeMapper() {
        assertDoesNotThrow { symbolProcess(CacheableArgumentWrongTypeMapper::class, AopSymbolProcessorProvider()) }
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

    @Test
    fun testInnerTypeCache() {
        compile0("""
        interface OuterType {
          @ru.tinkoff.kora.cache.annotation.Cache("test")
          interface MyCache : ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
