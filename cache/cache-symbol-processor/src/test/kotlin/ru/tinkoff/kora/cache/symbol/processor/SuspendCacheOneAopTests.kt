package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache1
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableSuspendOne
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendCacheOneAopTests : CaffeineCacheModule {

    private val CACHE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache1Impl"
    private val SERVICE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableSuspendOne__AopProxy"

    private var cache: DummyCache1? = null
    private var cachedService: CacheableSuspendOne? = null

    private fun getService(): CacheableSuspendOne {
        if (cachedService != null) {
            return cachedService as CacheableSuspendOne
        }

        return try {
            val classLoader = symbolProcess(
                listOf(DummyCache1::class, CacheableSuspendOne::class),
                CacheSymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )

            val cacheClass = classLoader.loadClass(CACHE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE_CLASS")
            cache = cacheClass.constructors[0].newInstance(
                CacheRunner.getConfig(),
                caffeineCacheFactory(null),
                caffeineCacheTelemetry(null, null)
            ) as DummyCache1

            val serviceClass = classLoader.loadClass(SERVICE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $SERVICE_CLASS")
            val inst = serviceClass.constructors[0].newInstance(cache) as CacheableSuspendOne
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
        val notCached = runBlocking { service.getValue("1") }
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1") }
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
        val initial = runBlocking { service.getValue("1") }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1") }
        assertEquals(cached, fromCache)
    }

    @Test
    fun getWrongKeyWhenCacheFilled() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1") }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("2") }
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
        val initial = runBlocking { service.getValue("2") }
        assertNotEquals(cached, initial)

        // then
        val fromCache = runBlocking { service.getValue("2") }
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
        val initial = runBlocking { service.getValue("1") }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"
        runBlocking { service.evictValue("1") }

        // then
        val fromCache = runBlocking { service.getValue("1") }
        assertNotEquals(cached, fromCache)
    }

    @Test
    fun getWhenCacheInvalidateAll() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1") }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"
        runBlocking { service.evictAll() }

        // then
        val fromCache = runBlocking { service.getValue("1") }
        assertNotEquals(cached, fromCache)
    }
}
