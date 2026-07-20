package io.koraframework.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.cache.caffeine.CaffeineCacheModule
import io.koraframework.cache.redis.RedisCacheClient
import io.koraframework.cache.redis.RedisCacheModule
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@KspExperimental
class AsyncCacheAopTests : AbstractSymbolProcessorTest(), CaffeineCacheModule, RedisCacheModule {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.cache.annotation.Cache;
            import io.koraframework.cache.annotation.CacheInvalidate;
            import io.koraframework.cache.annotation.CacheInvalidateAll;
            import io.koraframework.cache.annotation.CacheMode;
            import io.koraframework.cache.annotation.CachePut;
            import io.koraframework.cache.annotation.Cacheable;
            import io.koraframework.cache.caffeine.CaffeineCache;
            import io.koraframework.cache.redis.RedisCache;
            
            """.trimIndent()
    }

    @Test
    fun cacheableAsyncWithRedis() {
        compileRedis("""
            @Cacheable(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun getValue(arg1: String): String {
                return value
            }
            """)

        val client = BlockingRedisCacheClient()
        val cache = newRedisCache(client)
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance, Executors.newSingleThreadExecutor())
        client.blockSet()

        assertEquals("1", service.call("getValue", "1"))
        client.awaitBlocked()
        assertNull(cache.call("get", "1"))

        client.release()
        assertEventually { "1" == cache.call("get", "1") }
    }

    @Test
    fun cachePutAsyncWithRedis() {
        compileRedis("""
            @CachePut(value = DummyCache::class, args = ["arg1"], mode = CacheMode.ASYNC)
            open fun putValue(arg1: String): String {
                return value
            }
            """)

        val client = BlockingRedisCacheClient()
        val cache = newRedisCache(client)
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance, Executors.newSingleThreadExecutor())
        client.blockSet()

        assertEquals("1", service.call("putValue", "1"))
        client.awaitBlocked()
        assertNull(cache.call("get", "1"))

        client.release()
        assertEventually { "1" == cache.call("get", "1") }
    }

    @Test
    fun cacheInvalidateAsyncWithRedis() {
        compileRedis("""
            @CacheInvalidate(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun evictValue(arg1: String) {
            }
            """)

        val client = BlockingRedisCacheClient()
        val cache = newRedisCache(client)
        cache.call("put", "1", "1")
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance, Executors.newSingleThreadExecutor())
        client.blockDelOne()

        service.call("evictValue", "1")
        client.awaitBlocked()
        assertEquals("1", cache.call("get", "1"))

        client.release()
        assertEventually { cache.call("get", "1") == null }
    }

    @Test
    fun cacheInvalidateAllAsyncWithRedis() {
        compileRedis("""
            @CacheInvalidateAll(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun evictAll() {
            }
            """)

        val client = BlockingRedisCacheClient()
        val cache = newRedisCache(client)
        cache.call("put", "1", "1")
        cache.call("put", "2", "2")
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance, Executors.newSingleThreadExecutor())
        client.blockDelMany()

        service.call("evictAll")
        client.awaitBlocked()
        assertEquals("1", cache.call("get", "1"))

        client.release()
        assertEventually { cache.call("get", "1") == null }
        assertEventually { cache.call("get", "2") == null }
    }

    @Test
    fun cacheableAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @Cacheable(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun getValue(arg1: String): String {
                return value
            }
            """)

        val cache = newCaffeineCache()
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance)

        assertEquals("1", service.call("getValue", "1"))

        assertEquals("1", cache.call("get", "1"))
    }

    @Test
    fun cachePutAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CachePut(value = DummyCache::class, args = ["arg1"], mode = CacheMode.ASYNC)
            open fun putValue(arg1: String): String {
                return value
            }
            """)

        val cache = newCaffeineCache()
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance)

        assertEquals("1", service.call("putValue", "1"))

        assertEquals("1", cache.call("get", "1"))
    }

    @Test
    fun cacheInvalidateAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CacheInvalidate(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun evictValue(arg1: String) {
            }
            """)

        val cache = newCaffeineCache()
        cache.call("put", "1", "1")
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance)

        service.call("evictValue", "1")

        assertNull(cache.call("get", "1"))
    }

    @Test
    fun cacheInvalidateAllAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CacheInvalidateAll(value = DummyCache::class, mode = CacheMode.ASYNC)
            open fun evictAll() {
            }
            """)

        val cache = newCaffeineCache()
        cache.call("put", "1", "1")
        cache.call("put", "2", "2")
        val service = newObject("\$CacheableSync__AopProxy", cache.objectInstance)

        service.call("evictAll")

        assertNull(cache.call("get", "1"))
        assertNull(cache.call("get", "2"))
    }

    private fun compileRedis(method: String) {
        compile0(
            listOf(CacheSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Cache("dummy")
            interface DummyCache : RedisCache<String, String>
            """,
            """
            open class CacheableSync {
                var value = "1"
                
                $method
            }
            """
        ).assertSuccess()
    }

    private fun compileCaffeine(method: String) {
        compile0(
            listOf(CacheSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Cache("dummy")
            interface DummyCache : CaffeineCache<String, String>
            """,
            """
            open class CacheableSync {
                var value = "1"
                
                $method
            }
            """
        ).assertSuccess()
    }

    private fun newRedisCache(client: BlockingRedisCacheClient): TestObject {
        return newObject(
            "\$DummyCache_Impl",
            CacheRunner.getRedisConfig(),
            client,
            defaultRedisCacheTelemetryFactory(null, null, null, null),
            stringRedisCacheKeyMapper(),
            stringRedisCacheValueMapper()
        )
    }

    private fun newCaffeineCache(): TestObject {
        return newObject(
            "\$DummyCache_Impl",
            CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null),
            defaultCaffeineCacheTelemetryFactory(null, null, null, null)
        )
    }

    private fun assertEventually(assertion: () -> Boolean) {
        repeat(50) {
            if (assertion()) {
                return
            }
            Thread.sleep(100)
        }
        fail<Nothing>("Async cache operation didn't complete")
    }

    private fun TestObject.call(method: String, vararg args: Any?): Any? {
        val javaMethod = objectInstance.javaClass.methods.firstOrNull {
            it.name == method && it.parameterCount == args.size && it.parameterTypes.isAssignable(args)
        } ?: throw IllegalArgumentException("Method $method wasn't found")
        return javaMethod.invoke(objectInstance, *args)
    }

    private fun Array<Class<*>>.isAssignable(args: Array<out Any?>): Boolean {
        for (i in indices) {
            if (args[i] != null && !this[i].isAssignableFrom(args[i]!!.javaClass)) {
                return false
            }
        }
        return true
    }

    private class BlockingRedisCacheClient : RedisCacheClient {
        private val cache = ConcurrentHashMap<ByteBuffer?, ByteBuffer?>()
        private val delegate = CacheRunner.lettuceClient(cache)
        private var mode = Mode.NONE
        private var started = CountDownLatch(0)
        private var release = CountDownLatch(0)

        fun blockSet() = block(Mode.SET)

        fun blockDelOne() = block(Mode.DEL_ONE)

        fun blockDelMany() = block(Mode.DEL_MANY)

        fun awaitBlocked() {
            assertTrue(started.await(5, TimeUnit.SECONDS))
        }

        fun release() {
            release.countDown()
        }

        private fun block(mode: Mode) {
            this.mode = mode
            started = CountDownLatch(1)
            release = CountDownLatch(1)
        }

        private fun awaitRelease(mode: Mode) {
            if (this.mode != mode) {
                return
            }
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }

        override fun scan(prefix: ByteArray): List<ByteArray> = delegate.scan(prefix)

        override fun get(key: ByteArray): ByteArray? = delegate.get(key)

        override fun mget(keys: Array<ByteArray>): Map<ByteArray, ByteArray> = delegate.mget(keys)

        override fun getex(key: ByteArray, expireAfterMillis: Long): ByteArray? = delegate.getex(key, expireAfterMillis)

        override fun getex(keys: Array<ByteArray>, expireAfterMillis: Long): Map<ByteArray, ByteArray> = delegate.getex(keys, expireAfterMillis)

        override fun set(key: ByteArray, value: ByteArray) {
            awaitRelease(Mode.SET)
            delegate.set(key, value)
        }

        override fun mset(keyAndValue: MutableMap<ByteArray, ByteArray>) = delegate.mset(keyAndValue)

        override fun psetex(key: ByteArray, value: ByteArray, expireAfterMillis: Long) {
            awaitRelease(Mode.SET)
            delegate.psetex(key, value, expireAfterMillis)
        }

        override fun psetex(keyAndValue: MutableMap<ByteArray, ByteArray>, expireAfterMillis: Long) = delegate.psetex(keyAndValue, expireAfterMillis)

        override fun del(key: ByteArray): Long {
            awaitRelease(Mode.DEL_ONE)
            return delegate.del(key)
        }

        override fun del(keys: Array<ByteArray>): Long {
            awaitRelease(Mode.DEL_MANY)
            return delegate.del(keys)
        }

        override fun flushAll() = delegate.flushAll()

        private enum class Mode {
            NONE,
            SET,
            DEL_ONE,
            DEL_MANY
        }
    }
}
