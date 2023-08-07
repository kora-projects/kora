package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache22
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableSyncMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SyncManyCacheAopTests : CaffeineCacheModule {

    private val CACHE1_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache2Impl"
    private val CACHE2_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache22Impl"
    private val SERVICE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testdata.\$CacheableSyncMany__AopProxy"

    private var cache1: DummyCache2? = null
    private var cache2: DummyCache22? = null
    private var cachedService: CacheableSyncMany? = null

    private fun getService(): CacheableSyncMany {
        if (cachedService != null) {
            return cachedService as CacheableSyncMany;
        }

        return try {
            val classLoader = symbolProcess(
                listOf(DummyCache2::class, DummyCache22::class, CacheableSyncMany::class),
                AopSymbolProcessorProvider(),
                CacheSymbolProcessorProvider()
            )


            val cache1Class = classLoader.loadClass(CACHE1_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE1_CLASS")
            cache1 = cache1Class.constructors[0].newInstance(
                CacheRunner.getConfig(),
                caffeineCacheFactory(null),
                caffeineCacheTelemetry(null, null)
            ) as DummyCache2

            val cache2Class = classLoader.loadClass(CACHE2_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE2_CLASS")
            cache2 = cache2Class.constructors[0].newInstance(
                CacheRunner.getConfig(),
                caffeineCacheFactory(null),
                caffeineCacheTelemetry(null, null)
            ) as DummyCache22

            val serviceClass = classLoader.loadClass(SERVICE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $SERVICE_CLASS")
            val inst = serviceClass.constructors[0].newInstance(cache1, cache2) as CacheableSyncMany
            inst
        } catch (e: Exception) {
            throw IllegalStateException(e.message, e)
        }
    }

    @BeforeEach
    fun reset() {
        cache1?.invalidateAll()
        cache2?.invalidateAll()
    }

    @Test
    fun getWhenWasCacheEmpty() {
        // given
        val service = getService()
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
    fun getLevel2AndThenSaveCacheLevel1() {
        // given
        val service = getService()
        service.value = "1"
        assertNotNull(service)

        val cachedValue = "LEVEL_2"
        cache2!!.put(CacheKey.of("1", BigDecimal.ZERO), cachedValue)

        // when
        val valueFromLevel2 = service.getValue("1", BigDecimal.ZERO)
        service.value = "2"

        // then
        val valueFromLevel1 = service.getValue("1", BigDecimal.ZERO)
        assertEquals(valueFromLevel2, valueFromLevel1)
        assertEquals(cachedValue, valueFromLevel1)
    }

    @Test
    fun getWhenCacheFilled() {
        // given
        val service = getService()
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
    fun getWrongKeyWhenCacheFilled() {
        // given
        val service = getService()
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
    fun getWhenCacheFilledOtherKey() {
        // given
        val service = getService()
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
    fun getWhenCacheInvalidate() {
        // given
        val service = getService()
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
    fun getWhenCacheInvalidateAll() {
        // given
        val service = getService()
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
