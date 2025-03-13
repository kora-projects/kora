package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.cache.CacheKeyMapper
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.cache.redis.RedisCacheKeyMapper
import ru.tinkoff.kora.cache.redis.RedisCacheMapperModule
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache22
import ru.tinkoff.kora.cache.symbol.processor.testdata.CacheableSyncMany
import ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.CacheableSuspendMany
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class SuspendCacheManyAopTests : CaffeineCacheModule, RedisCacheMapperModule {

    private val CACHE1_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache21Impl"
    private val CACHE2_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testcache.\$DummyCache22Impl"
    private val SERVICE_CLASS = "ru.tinkoff.kora.cache.symbol.processor.testdata.suspended.\$CacheableSuspendMany__AopProxy"

    private var cache1: DummyCache21? = null
    private var cache2: DummyCache22? = null
    private var cachedService: CacheableSyncMany? = null

    private fun getService(): CacheableSuspendMany {
        if (cachedService != null) {
            return cachedService as CacheableSuspendMany;
        }

        return try {
            val classLoader = symbolProcess(
                listOf(DummyCache21::class, DummyCache22::class, CacheableSuspendMany::class),
                CacheSymbolProcessorProvider(),
                AopSymbolProcessorProvider(),
            )

            val cache1Class = classLoader.loadClass(CACHE1_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE1_CLASS")
            cache1 = cache1Class.constructors[0].newInstance(
                CacheRunner.getCaffeineConfig(),
                caffeineCacheFactory(null),
                defaultCacheTelemetryFactory(null, null, null)
            ) as DummyCache21

            val cache2Class = classLoader.loadClass(CACHE2_CLASS) ?: throw IllegalArgumentException("Expected class not found: $CACHE2_CLASS")
            val cache = mutableMapOf<ByteBuffer?, ByteBuffer?>()
            cache2 = cache2Class.constructors[0].newInstance(
                CacheRunner.getRedisConfig(),
                CacheRunner.lettuceSyncClient(cache),
                CacheRunner.lettuceAsyncClient(cache),
                defaultCacheTelemetryFactory(null, null, null),
                RedisCacheKeyMapper<DummyCache22.Key> { key ->
                    val k1 = key.k1.toByteArray(StandardCharsets.UTF_8)
                    val k2 = key.k2.toString().toByteArray(StandardCharsets.UTF_8)
                    ByteBuffer.allocate(k1.size + RedisCacheKeyMapper.DELIMITER.size + k2.size)
                        .put(k1)
                        .put(RedisCacheKeyMapper.DELIMITER)
                        .put(k2)
                        .array()
                },
                stringRedisValueMapper()
            ) as DummyCache22

            val serviceClass = classLoader.loadClass(SERVICE_CLASS) ?: throw IllegalArgumentException("Expected class not found: $SERVICE_CLASS")
            val mapper1 = CacheKeyMapper.CacheKeyMapper2<DummyCache21.Key, String, BigDecimal?>
            { arg1, arg2 -> DummyCache21.Key(arg1, arg2 ?: BigDecimal.ZERO) }
            val mapper2 = CacheKeyMapper.CacheKeyMapper2<DummyCache22.Key, String, BigDecimal?>
            { arg1, arg2 -> DummyCache22.Key(arg1, arg2 ?: BigDecimal.ZERO) }
            val inst = serviceClass.constructors[0].newInstance(cache1, mapper1, cache2, mapper2) as CacheableSuspendMany
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
        val notCached = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        service.value = "2"

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
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
        cache2!!.put(DummyCache22.Key("1", BigDecimal.ZERO), cachedValue)

        // when
        val valueFromLevel2 = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        service.value = "2"

        // then
        val valueFromLevel1 = runBlocking { service.getValue("1", BigDecimal.ZERO) }
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
        service.value = "2"
        runBlocking { service.evictValue("1", BigDecimal.ZERO) }

        // then
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
        service.value = "2"
        runBlocking { service.evictAll() }

        // then
        val fromCache = runBlocking { service.getValue("1", BigDecimal.ZERO) }
        assertNotEquals(cached, fromCache)
    }
}
