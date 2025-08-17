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
import kotlin.reflect.KClass

class CacheSymbolProcessorTests : AbstractSymbolProcessorTest() {
    fun processClass(clazz: KClass<*>) = symbolProcess(listOf(AopSymbolProcessorProvider()), listOf(clazz))

    @Test
    fun cacheKeyArgumentMissing() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableArgumentMissing::class) }
    }

    @Test
    fun cacheKeyMapper() {
        assertDoesNotThrow { processClass(CacheableMapper::class) }
    }

    @Test
    fun cacheRedisKeyMapperTagged() {
        assertDoesNotThrow { processClass(DummyCacheTagged::class) }
    }

    @Test
    fun cacheKeyArgumentWrongOrderMapper() {
        assertDoesNotThrow { processClass(CacheableArgumentWrongOrderMapper::class) }
    }

    @Test
    fun cacheKeyArgumentWrongTypeMapper() {
        assertDoesNotThrow { processClass(CacheableArgumentWrongTypeMapper::class) }
    }

    @Test
    fun cacheNamePatternMismatch() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableNameInvalid::class) }
    }

    @Test
    fun cacheGetForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableGetVoid::class) }
    }

    @Test
    fun cachePutForVoidSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheablePutVoid::class) }
    }

    @Test
    fun cacheGetForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableGetMono::class) }
    }

    @Test
    fun cachePutForMonoSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheablePutMono::class) }
    }

    @Test
    fun cacheGetForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableGetFlux::class) }
    }

    @Test
    fun cachePutForFluxSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheablePutFlux::class) }
    }

    @Test
    fun cacheGetForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheableGetPublisher::class) }
    }

    @Test
    fun cachePutForPublisherSignature() {
        assertThrows(
            CompilationErrorException::class.java
        ) { processClass(CacheablePutPublisher::class) }
    }

    @Test
    fun testInnerTypeCache() {
        compile0(listOf(AopSymbolProcessorProvider()), """
        interface OuterType {
          @ru.tinkoff.kora.cache.annotation.Cache("test")
          interface MyCache : ru.tinkoff.kora.cache.caffeine.CaffeineCache<String, String>
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
