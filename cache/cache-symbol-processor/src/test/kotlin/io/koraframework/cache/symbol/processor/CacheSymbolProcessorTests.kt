package io.koraframework.cache.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.cache.symbol.processor.testcache.DummyCacheTagged
import io.koraframework.cache.symbol.processor.testcache.DummyInheritFinal
import io.koraframework.cache.symbol.processor.testcache.DummyInheritMediator
import io.koraframework.cache.symbol.processor.testdata.*
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.reflect.KClass

class CacheSymbolProcessorTests : AbstractSymbolProcessorTest() {

    fun processClass(clazz: KClass<*>) = symbolProcess(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), listOf(clazz))

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
    fun cacheInheritFinalCacheScanner() {
        assertDoesNotThrow { processClass(DummyInheritFinal::class) }
    }

    @Test
    fun cacheInheritMediatorCacheScanner() {
        assertDoesNotThrow { processClass(DummyInheritMediator::class) }
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
    fun testInnerTypeCache() {
        compile0(listOf(AopSymbolProcessorProvider()), """
        interface OuterType {
          @io.koraframework.cache.annotation.Cache("test")
          interface MyCache : io.koraframework.cache.caffeine.CaffeineCache<String, String>
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
