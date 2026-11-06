package io.koraframework.cache.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.cache.symbol.processor.testcache.DummyCacheTagged
import io.koraframework.cache.symbol.processor.testcache.DummyInheritFinal
import io.koraframework.cache.symbol.processor.testcache.DummyInheritMediator
import io.koraframework.cache.symbol.processor.testdata.*
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.function.Supplier
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
    fun cacheableWithCacheExtendingExtraInterface() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            interface Marker
            """, """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : io.koraframework.cache.caffeine.CaffeineCache<String, String>, Marker
            """, """
            open class Svc {
                @io.koraframework.cache.annotation.Cacheable(C1::class)
                open fun get(id: String): String = id
            }
            """)
        compileResult.assertSuccess()
    }

    @Test
    fun cacheableWithPartiallyBoundParentCache() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            interface StringKeyCache<V> : io.koraframework.cache.caffeine.CaffeineCache<String, V>
            """, """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : StringKeyCache<Int>
            """, """
            open class Svc {
                @io.koraframework.cache.annotation.Cacheable(C1::class)
                open fun get(id: String): Int = 1
            }
            """)
        compileResult.assertSuccess()
    }

    @Test
    fun redisCacheThroughGenericParentInterface() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            interface BaseRedis<K : Any, V : Any> : io.koraframework.cache.redis.RedisCache<K, V>
            """, """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : BaseRedis<C1.Key, String> {
                data class Key(val a: String, val b: String)
            }
            """)
        compileResult.assertSuccess()
    }

    @Test
    fun cacheKeyConstructorWithSupertypeProperty() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : io.koraframework.cache.caffeine.CaffeineCache<C1.Key, String> {
                data class Key(val a: CharSequence, val b: Long)
            }
            """, """
            open class Svc {
                @io.koraframework.cache.annotation.Cacheable(C1::class)
                open fun get(a: String, b: Long): String = a + b
            }
            """)
        compileResult.assertSuccess()
        val constructor = loadClass("\$Svc__AopProxy").constructors[0]
        assertEquals(1, constructor.parameterCount, "Key constructor expected, but proxy requires " + constructor.parameterTypes.toList())
    }

    @Test
    fun cacheKeyMapperForNarrowerConstructorProperty() {
        compile0(listOf(AopSymbolProcessorProvider(), CacheSymbolProcessorProvider()), """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : io.koraframework.cache.caffeine.CaffeineCache<C1.Key, String> {
                data class Key(val a: String, val b: Long)
            }
            """, """
            open class Svc {
                @io.koraframework.cache.annotation.Cacheable(C1::class)
                open fun get(a: CharSequence, b: Long): String = a.toString() + b
            }
            """)
        compileResult.assertSuccess()
        val constructor = loadClass("\$Svc__AopProxy").constructors[0]
        assertEquals(2, constructor.parameterCount, "CacheKeyMapper2 expected, but proxy requires " + constructor.parameterTypes.toList())
    }

    @Test
    fun twoRedisCachesSharingDataClassKey() {
        compile0(listOf(KoraAppProcessorProvider(), CacheSymbolProcessorProvider()), """
            data class UserKey(val tenant: String, val id: String)
            """, """
            @io.koraframework.cache.annotation.Cache("cache.users")
            interface UserCache : io.koraframework.cache.redis.RedisCache<UserKey, String>
            """, """
            @io.koraframework.cache.annotation.Cache("cache.profiles")
            interface ProfileCache : io.koraframework.cache.redis.RedisCache<UserKey, String>
            """, """
            @KoraApp
            interface App : io.koraframework.cache.redis.RedisCacheModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                fun config(): io.koraframework.config.common.Config = io.koraframework.config.common.util.ConfigMappingUtils.fromMap(
                    mapOf("cache" to mapOf("users" to mapOf("keyPrefix" to "u"), "profiles" to mapOf("keyPrefix" to "p")))
                )

                fun redisCacheClient(): io.koraframework.cache.redis.RedisCacheClient = org.mockito.Mockito.mock(io.koraframework.cache.redis.RedisCacheClient::class.java)

                @Root
                fun root(users: UserCache, profiles: ProfileCache): String = ""
            }
            """)
        compileResult.assertSuccess()
        @Suppress("UNCHECKED_CAST")
        val draw = (loadClass("AppGraph").constructors[0].newInstance() as Supplier<ApplicationGraphDraw>).get()
        draw.init().release()
    }

    @Test
    fun redisCacheWithDataClassKeyWhoseMapperModuleIsOnClasspath() {
        // LibraryRedisKey and its `$LibraryRedisKey_RedisCacheKeyMapperModule` come from another compilation (test classpath)
        // and the app does not wire that module, so the app's own compilation must generate the mapper module again
        compile0(listOf(KoraAppProcessorProvider(), CacheSymbolProcessorProvider()), """
            @io.koraframework.cache.annotation.Cache("cache.profiles")
            interface ProfileCache : io.koraframework.cache.redis.RedisCache<io.koraframework.cache.symbol.processor.testcache.LibraryRedisKey, String>
            """, """
            @KoraApp
            interface App : io.koraframework.cache.redis.RedisCacheModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                fun config(): io.koraframework.config.common.Config = io.koraframework.config.common.util.ConfigMappingUtils.fromMap(
                    mapOf("cache" to mapOf("profiles" to mapOf("keyPrefix" to "p")))
                )

                fun redisCacheClient(): io.koraframework.cache.redis.RedisCacheClient = org.mockito.Mockito.mock(io.koraframework.cache.redis.RedisCacheClient::class.java)

                @Root
                fun root(profiles: ProfileCache): String = ""
            }
            """)
        compileResult.assertSuccess()
        @Suppress("UNCHECKED_CAST")
        val draw = (loadClass("AppGraph").constructors[0].newInstance() as Supplier<ApplicationGraphDraw>).get()
        draw.init().release()
    }

    @Test
    fun redisDataClassKeyWithCacheExtendingExtraInterface() {
        compile0(listOf(CacheSymbolProcessorProvider()), """
            interface Marker
            """, """
            @io.koraframework.cache.annotation.Cache("c1")
            interface C1 : io.koraframework.cache.redis.RedisCache<C1.Key, String>, Marker {
                data class Key(val a: String, val b: String)
            }
            """)
        compileResult.assertSuccess()
    }
}
