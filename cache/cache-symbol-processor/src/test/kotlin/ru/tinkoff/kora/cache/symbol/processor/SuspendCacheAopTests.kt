package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableTargetSync
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableTargetSyncMany
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableTargetSuspend
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendCacheAopTests : Assertions() {

    private val CACHED_SERVICE = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableTargetSuspend__AopProxy"

    private val cacheManager = DummyCacheManager<Any, Any>()
    private var cachedService: CacheableTargetSuspend? = null

    private fun getService(manager: DummyCacheManager<Any, Any>): CacheableTargetSuspend {
        if(cachedService != null) {
            return cachedService as CacheableTargetSuspend;
        }

        return try {
            val classLoader = symbolProcess(
                CacheableTargetSuspend::class,
                CacheKeySymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )
            val serviceClass = classLoader.loadClass(CACHED_SERVICE) ?: throw IllegalArgumentException("Expected class not found: $CACHED_SERVICE")
            val inst = serviceClass.constructors[0].newInstance(manager) as CacheableTargetSuspend
            cachedService = inst
            inst
        } catch (e: Exception) {
            throw IllegalStateException(e.message, e)
        }
    }

    @BeforeEach
    fun reset() {
        cacheManager.reset()
    }

    @Test
    fun getFromCacheWhenWasCacheEmpty() {
        // given
        val service = getService(cacheManager)
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
    fun getFromCacheWhenCacheFilled() {
        // given
        val service = getService(cacheManager)
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
    fun getFromCacheWrongKeyWhenCacheFilled() {
        // given
        val service = getService(cacheManager)
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
    fun getFromCacheWhenCacheFilledOtherKey() {
        // given
        val service = getService(cacheManager)
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
    fun getFromCacheWhenCacheInvalidate() {
        // given
        val service = getService(cacheManager)
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"
        runBlocking { service.evictValue("1", BigDecimal.ZERO) }

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
    }

    @Test
    fun getFromCacheWhenCacheInvalidateAll() {
        // given
        val service = getService(cacheManager)
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        val cached = runBlocking { service.putValue(BigDecimal.ZERO, "5", "1") }
        assertEquals(initial, cached)
        service.value = "2"
        runBlocking { service.evictAll() }

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
    }
}
