package io.koraframework.cache.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.cache.redis.mapper.RedisCacheKeyMapper
import io.koraframework.cache.redis.mapper.RedisCacheMapperModule
import io.koraframework.cache.symbol.processor.testcache.DummyCacheTagged
import io.koraframework.cache.symbol.processor.testcache.DummyInheritFinal
import io.koraframework.cache.symbol.processor.testcache.DummyInheritMediator
import io.koraframework.cache.symbol.processor.testdata.*
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
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
    fun redisDataClassKeyComponentsWithDelimiterDoNotCollide() {
        compile0(listOf(CacheSymbolProcessorProvider()), """
            data class Key(val a: String, val b: String)
            """.trimIndent(), """
            @io.koraframework.cache.annotation.Cache("cache.k")
            interface KCache : io.koraframework.cache.redis.RedisCache<Key, String>
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val moduleClass = loadClass("\$KCache_Module")
        val module = Proxy.newProxyInstance(moduleClass.classLoader, arrayOf(moduleClass)) { proxy, method, args -> InvocationHandler.invokeDefault(proxy, method, *args) }
        val factory = moduleClass.methods.first { it.name.endsWith("_RedisKeyMapper") }
        val stringMapper = object : RedisCacheMapperModule {}.stringRedisCacheKeyMapper()
        @Suppress("UNCHECKED_CAST")
        val mapper = factory.invoke(module, stringMapper, stringMapper) as RedisCacheKeyMapper<Any>

        val key = loadClass("Key").getConstructor(String::class.java, String::class.java)
        val k1 = mapper.apply(key.newInstance("x:y", "z"))
        val k2 = mapper.apply(key.newInstance("x", "y:z"))

        assertFalse(k1.contentEquals(k2), "Key(x:y, z) and Key(x, y:z) must not share a cache key")
        assertArrayEquals("3:x:y:1:z".toByteArray(), k1)
    }

    @Test
    fun cacheThroughGenericParentWithNestedTypeArgument() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            interface ListKeyCache<K> : io.koraframework.cache.caffeine.CaffeineCache<List<K>, String>
            """.trimIndent(), """
            @io.koraframework.cache.annotation.Cache("cache1")
            interface Cache1 : ListKeyCache<String>
            """.trimIndent(), """
            open class Svc {
                @io.koraframework.cache.annotation.Cacheable(Cache1::class)
                open fun get(k: List<String>): String = "x"
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
