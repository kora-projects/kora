package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableTargetSuspendMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendManyCacheAopTests : Assertions() {

    private val CACHED_SERVICE = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableTargetSuspendMany__AopProxy"

    private val cacheManager = DummyCacheManager<Any, Any>()
    private var cachedService: CacheableTargetSuspendMany? = null

    private fun getService(manager: DummyCacheManager<Any, Any>): CacheableTargetSuspendMany {
        if(cachedService != null) {
            return cachedService as CacheableTargetSuspendMany;
        }

        return try {
            val classLoader = symbolProcess(
                CacheableTargetSuspendMany::class,
                CacheKeySymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )
            val serviceClass = classLoader.loadClass(CACHED_SERVICE) ?: throw IllegalArgumentException("Expected class not found: $CACHED_SERVICE")
            val inst = serviceClass.constructors[0].newInstance(manager) as CacheableTargetSuspendMany
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
    fun getFromCacheLevel2AndThenSaveCacheLevel1() {
        // given
        val service = getService(cacheManager)
        val cache1 = cacheManager.getCache("suspend_cache")
        val cache2 = cacheManager.getCache("suspend_cache_2")
        service.value = "1"
        assertNotNull(service)
        assertTrue(cache1.isEmpty())
        assertTrue(cache2.isEmpty())
        val cachedValue = "LEVEL_2"
        val cachedKey: CacheKey = object : CacheKey {
            override fun values(): List<Any> {
                return listOf<Any>("1", BigDecimal.ZERO)
            }

            override fun toString(): String {
                return "1" + "-" + BigDecimal.ZERO
            }
        }
        cache2.put(cachedKey, cachedValue)
        assertFalse(cache2.isEmpty())

        // when
        val valueFromLevel2 = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        service.value = "2"

        // then
        val valueFromLevel1 = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertEquals(valueFromLevel2, valueFromLevel1)
        assertEquals(cachedValue, valueFromLevel1)
        assertFalse(cache1.isEmpty())
        assertFalse(cache2.isEmpty())
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
