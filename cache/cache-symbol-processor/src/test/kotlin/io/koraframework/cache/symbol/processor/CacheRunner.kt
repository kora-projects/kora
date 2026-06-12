package io.koraframework.cache.symbol.processor

import io.koraframework.cache.caffeine.`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor`
import io.koraframework.cache.caffeine.`$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor`
import io.koraframework.cache.caffeine.CaffeineCacheConfig
import io.koraframework.cache.caffeine.CaffeineCacheConfig.CaffeineTelemetryConfig
import io.koraframework.cache.redis.*
import io.koraframework.cache.redis.RedisCacheClient
import io.koraframework.cache.redis.telemetry.`$RedisCacheTelemetryConfig_ConfigValueExtractor`
import io.koraframework.cache.redis.telemetry.`$RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor`
import io.koraframework.cache.redis.telemetry.`$RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor`
import io.koraframework.cache.redis.telemetry.`$RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor`
import org.mockito.Mockito
import org.mockito.Mockito.`when`
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
                override fun telemetry() = `$RedisCacheTelemetryConfig_ConfigValueExtractor`.RedisCacheTelemetryConfig_Impl(
                    `$RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor`.RedisCacheLoggingConfig_Defaults(),
                    `$RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor`.RedisCacheTracingConfig_Defaults(),
                    `$RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor`.RedisCacheMetricsConfig_Defaults()
                )
            }
        }

        fun lettuceClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): RedisCacheClient {
            return object : RedisCacheClient {

                override fun scan(prefix: ByteArray): List<ByteArray> {
                    val keys: MutableList<ByteArray> = ArrayList()
                    for (buffer in cache.keys) {
                        if (buffer != null) {
                            if (buffer.array().copyOf(prefix.size).contentEquals(prefix)) {
                                keys.add(buffer.array())
                            }
                        }
                    }
                    return keys
                }

                override fun get(key: ByteArray): ByteArray? {
                    val r = cache[ByteBuffer.wrap(key)]
                    return r?.array()
                }

                override fun mget(keys: Array<ByteArray>): Map<ByteArray, ByteArray> {
                    val result: MutableMap<ByteArray, ByteArray> = HashMap()
                    for (key in keys) {
                        Optional.ofNullable(cache[ByteBuffer.wrap(key)]).ifPresent { r: ByteBuffer ->
                            result[key] = r.array()
                        }
                    }
                    return result
                }

                override fun getex(key: ByteArray, expireAfterMillis: Long): ByteArray? {
                    return get(key)
                }

                override fun getex(keys: Array<ByteArray>, expireAfterMillis: Long): Map<ByteArray, ByteArray> {
                    return mget(keys)
                }

                override fun set(key: ByteArray, value: ByteArray) {
                    cache[ByteBuffer.wrap(key)] = ByteBuffer.wrap(value)
                }

                override fun mset(keyAndValue: MutableMap<ByteArray, ByteArray>) {
                    keyAndValue.forEach { (k, v) -> set(k, v) }
                }

                override fun psetex(keyAndValue: MutableMap<ByteArray, ByteArray>, expireAfterMillis: Long) {
                    mset(keyAndValue)
                }

                override fun psetex(key: ByteArray, value: ByteArray, expireAfterMillis: Long) {
                    return set(key, value)
                }

                override fun del(key: ByteArray): Long {
                    val res = if (cache.remove(ByteBuffer.wrap(key)) == null) 0L else 1L
                    return res
                }

                override fun del(keys: Array<ByteArray>): Long {
                    var counter = 0
                    for (key in keys) {
                        counter += del(key).toInt()
                    }
                    return counter.toLong()
                }

                override fun flushAll() {
                    cache.clear()
                }
            }
        }
    }
}
