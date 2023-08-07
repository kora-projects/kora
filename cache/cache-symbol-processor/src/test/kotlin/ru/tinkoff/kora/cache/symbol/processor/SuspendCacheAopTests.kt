package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableSuspend
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendCacheAopTests : CaffeineCacheModule {

    private val CACHE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache2Impl"
    private val SERVICE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableSuspend__AopProxy"

    private var cache: DummyCache2? = null
    private var cachedService: CacheableSuspend? = null

    private fun getService(): CacheableSuspend {
        if (cachedService != null) {
            return cachedService as CacheableSuspend;
        }

        return try {
            val classLoader = symbolProcess(
                listOf(DummyCache2::class, CacheableSuspend::class),
                CacheSymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )

            val cacheClass = classLoader.loadClass(CACHE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE_CLASS")
            cache = cacheClass.constructors[0].newInstance(
                CacheRunner.getConfig(),
                caffeineCacheFactory(null),
                caffeineCacheTelemetry(null, null)
            ) as DummyCache2

            val serviceClass = classLoader.loadClass(SERVICE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $SERVICE_CLASS")
            val inst = serviceClass.constructors[0].newInstance(cache) as CacheableSuspend
            inst
        } catch (e: Exception) {
            throw IllegalStateException(e.message, e)
        }
    }

    @BeforeEach
    fun reset() {
        cache?.invalidateAll()
    }

    @Test
    fun getWhenWasCacheEmpty() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val notCached = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertEquals(notCached, fromCache)
        assertNotEquals("2", fromCache)
    }

    @Test
    fun getWhenCacheFilled() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertEquals(cached, fromCache)
    }

    @Test
    fun getWrongKeyWhenCacheFilled() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("2", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
        assertEquals(service.value, fromCache)
    }

    @Test
    fun getWhenCacheFilledOtherKey() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        service.value = "2"
        val initial = runBlocking { service.getValue("2", BigDecimal.ZERO) }
        assertNotEquals(cached, initial)

        // then
        val fromCache = runBlocking { service.getValue("2", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
        assertEquals(initial, fromCache)
    }

    @Test
    fun getWhenCacheInvalidate() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)

        val cached2 = runBlocking { service.putValue(BigDecimal.ZERO, "5", "2") }
        assertEquals(initial, cached2)

        service.value = "2"
        runBlocking { service.evictValue("1", BigDecimal.ZERO) }

        // then
        assertNull(cache!!.get(CacheKey.of("1", BigDecimal.ZERO)))
        assertEquals(cached2, cache!!.get(CacheKey.of("2", BigDecimal.ZERO)))

        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
    }

    @Test
    fun getWhenCacheInvalidateAll() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)

        val cached2 = runBlocking { service.putValue(BigDecimal.ZERO, "5", "2") }
        assertEquals(initial, cached2)

        service.value = "2"
        runBlocking { service.evictAll() }

        // then
        assertNull(cache!!.get(CacheKey.of("1", BigDecimal.ZERO)))
        assertNull(cache!!.get(CacheKey.of("2", BigDecimal.ZERO)))

        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
    }
}
