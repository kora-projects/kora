package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.cache.redis.RedisCacheMapperModule
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache11
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache12
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableSuspendOneMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendCacheOneManyAopTests : CaffeineCacheModule, RedisCacheMapperModule {

    private val CACHE1_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache11Impl"
    private val CACHE2_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache12Impl"
    private val SERVICE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableSuspendOneMany__AopProxy"

    private var cache1: DummyCache11? = null
    private var cache2: DummyCache12? = null
    private var cachedService: CacheableSuspendOneMany? = null

    private fun getService(): CacheableSuspendOneMany {
        if (cachedService != null) {
            return cachedService as CacheableSuspendOneMany;
        }

        return try {
            val classLoader = symbolProcess(
                listOf(DummyCache11::class, DummyCache12::class, CacheableSuspendOneMany::class),
                AopSymbolProcessorProvider(),
                CacheSymbolProcessorProvider()
            )


            val cache1Class = classLoader.loadClass(CACHE1_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE1_CLASS")
            cache1 = cache1Class.constructors[0].newInstance(
                CacheRunner.getCaffeineConfig(),
                caffeineCacheFactory(null),
                caffeineCacheTelemetry(null, null)
            ) as DummyCache11

            val cache2Class = classLoader.loadClass(CACHE2_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE2_CLASS")
            val cache = mutableMapOf<ByteBuffer?, ByteBuffer?>()
            cache2 = cache2Class.constructors[0].newInstance(
                CacheRunner.getRedisConfig(),
                CacheRunner.lettuceClient(cache),
                redisCacheTelemetry(null, null),
                stringRedisKeyMapper(),
                stringRedisValueMapper()
            ) as DummyCache12

            val serviceClass = classLoader.loadClass(SERVICE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $SERVICE_CLASS")
            val inst = serviceClass.constructors[0].newInstance(cache1, cache2) as CacheableSuspendOneMany
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
        val notCached = runBlocking { service.getValue("1") }
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1") }
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
        cache2!!.put("1", cachedValue)

        // when
        val valueFromLevel2 = runBlocking { service.getValue("1") }
        service.value = "2"

        // then
        val valueFromLevel1 = runBlocking { service.getValue("1") }
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
