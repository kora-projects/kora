package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableTargetSyncMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SyncManyCacheAopTests : Assertions() {

    private val CACHED_SERVICE = "ru.tinkoff.kora.cache.symbol.processor.testdata.\$CacheableTargetSyncMany__AopProxy"

    private val cacheManager = DummyCacheManager<Any, Any>()
    private var cachedService: CacheableTargetSyncMany? = null

    private fun getService(manager: DummyCacheManager<Any, Any>): CacheableTargetSyncMany {
        if(cachedService != null) {
            return cachedService as CacheableTargetSyncMany;
        }

        return try {
            val classLoader = symbolProcess(
                CacheableTargetSyncMany::class,
                AopSymbolProcessorProvider(),
                CacheKeySymbolProcessorProvider()
            )
            val serviceClass = classLoader.loadClass(CACHED_SERVICE) ?: throw IllegalArgumentException("Expected class not found: $CACHED_SERVICE")
            val instance = serviceClass.constructors[0].newInstance(manager) as CacheableTargetSyncMany
            cachedService = instance;
            instance
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
        val notCached = service.getValue("1", BigDecimal.ZERO)
        service.value = "2"

        // then
        val fromCache = service.getValue("1", BigDecimal.ZERO)
        assertEquals(notCached, fromCache)
        assertNotEquals("2", fromCache)
    }

    @Test
    fun getFromCacheLevel2AndThenSaveCacheLevel1() {
        // given
        val service = getService(cacheManager)
        val cache1 = cacheManager.getCache("sync_cache")
        val cache2 = cacheManager.getCache("sync_cache_2")
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
        val valueFromLevel2 = service.getValue("1", BigDecimal.ZERO)
        service.value = "2"

        // then
        val valueFromLevel1 = service.getValue("1", BigDecimal.ZERO)
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
        val initial = service.getValue("1", BigDecimal.ZERO)
        val cached = service.putValue(BigDecimal.ZERO, "5", "1")
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = service.getValue("1", BigDecimal.ZERO)
        assertEquals(cached, fromCache)
    }

    @Test
    fun getFromCacheWrongKeyWhenCacheFilled() {
        // given
        val service = getService(cacheManager)
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = service.getValue("1", BigDecimal.ZERO)
        val cached = service.putValue(BigDecimal.ZERO, "5", "1")
        assertEquals(initial, cached)
        service.value = "2"

        // then
        val fromCache = service.getValue("2", BigDecimal.ZERO)
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
        val cached = service.putValue(BigDecimal.ZERO, "5", "1")
        service.value = "2"
        val initial = service.getValue("2", BigDecimal.ZERO)
        assertNotEquals(cached, initial)

        // then
        val fromCache = service.getValue("2", BigDecimal.ZERO)
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
        val initial = service.getValue("1", BigDecimal.ZERO)
        val cached = service.putValue(BigDecimal.ZERO, "5", "1")
        assertEquals(initial, cached)
        service.value = "2"
        service.evictValue("1", BigDecimal.ZERO)

        // then
        val fromCache = service.getValue("1", BigDecimal.ZERO)
        assertNotEquals(cached, fromCache)
    }

    @Test
    fun getFromCacheWhenCacheInvalidateAll() {
        // given
        val service = getService(cacheManager)
        service.value = "1"
        assertNotNull(service)

        // when
        val initial = service.getValue("1", BigDecimal.ZERO)
        val cached = service.putValue(BigDecimal.ZERO, "5", "1")
        assertEquals(initial, cached)
        service.value = "2"
        service.evictAll()

        // then
        val fromCache = service.getValue("1", BigDecimal.ZERO)
        assertNotEquals(cached, fromCache)
    }
}
