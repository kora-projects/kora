package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableTargetSync
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableTargetSyncMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SyncCacheAopTests : Assertions() {

    private val CACHED_SERVICE = "ru.tinkoff.kora.cache.symbol.processor.testdata.\$CacheableTargetSync__AopProxy"

    private val cacheManager = DummyCacheManager<Any, Any>()
    private var cachedService: CacheableTargetSync? = null

    private fun getService(manager: DummyCacheManager<Any, Any>): CacheableTargetSync {
        if(cachedService != null) {
            return cachedService as CacheableTargetSync;
        }

        return try {
            val classLoader = symbolProcess(
                CacheableTargetSync::class,
                CacheKeySymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )
            val serviceClass = classLoader.loadClass(CACHED_SERVICE) ?: throw IllegalArgumentException("Expected class not found: $CACHED_SERVICE")
            val inst = serviceClass.constructors[0].newInstance(manager) as CacheableTargetSync
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
        val notCached = service.getValue("1", BigDecimal.ZERO)
        service.value = "2"

        // then
        val fromCache = service.getValue("1", BigDecimal.ZERO)
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
