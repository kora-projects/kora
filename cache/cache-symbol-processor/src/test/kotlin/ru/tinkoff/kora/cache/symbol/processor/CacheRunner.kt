package ru.tinkoff.kora.cache.symbol.processor

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import ru.tinkoff.kora.cache.caffeine.`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor`
import ru.tinkoff.kora.cache.caffeine.`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor`
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig.CaffeineTelemetryConfig
import ru.tinkoff.kora.cache.redis.RedisCacheClient
import ru.tinkoff.kora.cache.redis.RedisCacheConfig
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage


class CacheRunner {

    companion object {

        fun getCaffeineConfig(): CaffeineCacheConfig {
            val config = Mockito.mock(CaffeineCacheConfig::class.java)
            val telemetry = Mockito.mock(CaffeineTelemetryConfig::class.java)
            `when`(config.telemetry()).thenReturn(telemetry)
            `when`(config.maximumSize()).thenReturn(100000L)
            `when`(config.expireAfterAccess()).thenReturn(null)
            `when`(config.expireAfterWrite()).thenReturn(null)
            `when`(telemetry.metrics()).thenReturn(`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor`.CaffeineMetricsConfig_Defaults())
            `when`(telemetry.logging()).thenReturn(`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor`.CaffeineLoggingConfig_Defaults())
            return config
        }

        fun getRedisConfig(): RedisCacheConfig {
            return object : RedisCacheConfig {

                override fun keyPrefix(): String = "pref"

                override fun expireAfterWrite(): Duration? = null

                override fun expireAfterAccess(): Duration? = null
            }
        }

        fun lettuceClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): RedisCacheClient {
            return object : RedisCacheClient {

                override fun scan(prefix: ByteArray): CompletionStage<List<ByteArray>> {
                    val keys: MutableList<ByteArray> = ArrayList()
                    for (buffer in cache.keys) {
                        if (buffer != null) {
                            if (buffer.array().copyOf(prefix.size).contentEquals(prefix)) {
                                keys.add(buffer.array())
                            }
                        }
                    }
                    return CompletableFuture.completedFuture(keys)
                }

                override fun get(key: ByteArray): CompletionStage<ByteArray?> {
                    val r = cache[ByteBuffer.wrap(key)]
                    return CompletableFuture.completedFuture(r?.array())
                }

                override fun mget(keys: Array<ByteArray>): CompletionStage<Map<ByteArray, ByteArray>> {
                    val result: MutableMap<ByteArray, ByteArray> = HashMap()
                    for (key in keys) {
                        Optional.ofNullable(cache[ByteBuffer.wrap(key)]).ifPresent { r: ByteBuffer ->
                            result[key] = r.array()
                        }
                    }
                    return CompletableFuture.completedFuture(result)
                }

                override fun getex(key: ByteArray, expireAfterMillis: Long): CompletionStage<ByteArray?> {
                    return get(key)
                }

                override fun getex(keys: Array<ByteArray>, expireAfterMillis: Long): CompletionStage<Map<ByteArray, ByteArray>> {
                    return mget(keys)
                }

                override fun set(key: ByteArray, value: ByteArray) : CompletionStage<Boolean> {
                    cache[ByteBuffer.wrap(key)] = ByteBuffer.wrap(value)
                    return CompletableFuture.completedFuture(true)
                }

                override fun mset(keyAndValue: MutableMap<ByteArray, ByteArray>) : CompletionStage<Boolean> {
                    keyAndValue.forEach { (k, v) -> set(k, v) }
                    return CompletableFuture.completedFuture(true)
                }

                override fun psetex(keyAndValue: MutableMap<ByteArray, ByteArray>, expireAfterMillis: Long): CompletionStage<Boolean> {
                    mset(keyAndValue)
                    return CompletableFuture.completedFuture(true)
                }

                override fun psetex(key: ByteArray, value: ByteArray, expireAfterMillis: Long): CompletionStage<Boolean> {
                    return set(key, value)
                }

                override fun del(key: ByteArray): CompletionStage<Long> {
                    val res = if (cache.remove(ByteBuffer.wrap(key)) == null) 0L else 1L
                    return CompletableFuture.completedFuture(res)
                }

                override fun del(keys: Array<ByteArray>): CompletionStage<Long> {
                    var counter = 0
                    for (key in keys) {
                        counter += runBlocking { del(key).await().toInt() }
                    }
                    return CompletableFuture.completedFuture(counter.toLong())
                }

                override fun flushAll() : CompletionStage<Boolean> {
                    cache.clear()
                    return CompletableFuture.completedFuture(true)
                }
            }
        }
    }
}
