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
    fun cacheAsyncMode() {
        assertDoesNotThrow { processClass(CacheableAsync::class) }
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
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
        interface OuterType {
          @io.koraframework.cache.annotation.Cache("test")
          interface MyCache : io.koraframework.cache.caffeine.CaffeineCache<String, String>
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testTypealiasCacheKey() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
        typealias Key = String

        @io.koraframework.cache.annotation.Cache("test")
        interface MyCache : io.koraframework.cache.caffeine.CaffeineCache<Key, String>

        open class CacheableService {
          @io.koraframework.cache.annotation.Cacheable(MyCache::class)
          open fun get(key: Key): String = key

          @io.koraframework.cache.annotation.CachePut(MyCache::class, args = ["key"])
          open fun put(key: Key, value: String): String = value

          @io.koraframework.cache.annotation.CacheInvalidate(MyCache::class)
          open fun evict(key: Key) {}
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testTypealiasCompositeCacheKey() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
        data class CompositeKey(val id: String, val version: Int)
        typealias Key = CompositeKey

        @io.koraframework.cache.annotation.Cache("test")
        interface MyCache : io.koraframework.cache.caffeine.CaffeineCache<Key, String>

        @io.koraframework.cache.annotation.Cache("test_redis")
        interface MyRedisCache : io.koraframework.cache.redis.RedisCache<Key, String>

        open class CacheableService {
          @io.koraframework.cache.annotation.Cacheable(MyCache::class)
          @io.koraframework.cache.annotation.Cacheable(MyRedisCache::class)
          open fun get(id: String, version: Int): String = id + version

          @io.koraframework.cache.annotation.CacheInvalidate(MyCache::class)
          open fun evict(id: String, version: Int) {}
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        val module = loadClass("\$MyRedisCache_Module")
        org.junit.jupiter.api.Assertions.assertTrue(module.methods.any { it.name.endsWith("_RedisKeyMapper") })
    }
}
