package ru.tinkoff.kora.cache.symbol.processor

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient
import ru.tinkoff.kora.cache.redis.RedisCacheClient
import ru.tinkoff.kora.cache.redis.RedisCacheConfig
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_ConfigValueExtractor`.TelemetryConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_LogConfig_ConfigValueExtractor`.LogConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_MetricsConfig_ConfigValueExtractor`.MetricsConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_TracingConfig_ConfigValueExtractor`.TracingConfig_Impl
import ru.tinkoff.kora.telemetry.common.TelemetryConfig
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class CacheRunner {

    companion object {

        fun getCaffeineConfig(): CaffeineCacheConfig {
            return object : CaffeineCacheConfig {
                override fun expireAfterWrite(): Duration? {
                    return null;
                }

                override fun expireAfterAccess(): Duration? {
                    return null;
                }

                override fun initialSize(): Int? {
                    return null;
                }

                override fun telemetry(): TelemetryConfig {
                    return TelemetryConfig_Impl(LogConfig_Impl(false), TracingConfig_Impl(false), MetricsConfig_Impl(false, doubleArrayOf()))
                }
            }
        }

        fun getRedisConfig(): RedisCacheConfig {
            return object : RedisCacheConfig {

                override fun keyPrefix(): String = "pref"

                override fun expireAfterWrite(): Duration? = null

                override fun expireAfterAccess(): Duration? = null

                override fun telemetry(): TelemetryConfig {
                    return TelemetryConfig_Impl(LogConfig_Impl(false), TracingConfig_Impl(false), MetricsConfig_Impl(false, doubleArrayOf()))
                }
            }
        }

        fun lettuceAsyncClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): RedisCacheAsyncClient {
            return object : RedisCacheAsyncClient {
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

                override fun set(key: ByteArray, value: ByteArray): CompletionStage<Void> {
                    cache[ByteBuffer.wrap(key)] = ByteBuffer.wrap(value)
                    return CompletableFuture.completedFuture(null)
                }

                override fun mset(keyAndValue: MutableMap<ByteArray, ByteArray>): CompletionStage<Void> {
                    keyAndValue.forEach { (k, v) -> set(k, v) }
                    return CompletableFuture.completedFuture(null)
                }

                override fun psetex(keyAndValue: MutableMap<ByteArray, ByteArray>, expireAfterMillis: Long): CompletionStage<Void> {
                    mset(keyAndValue)
                    return CompletableFuture.completedFuture(null)
                }

                override fun psetex(key: ByteArray, value: ByteArray, expireAfterMillis: Long): CompletionStage<Void> {
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

                override fun flushAll(): CompletionStage<Void> {
                    cache.clear()
                    return CompletableFuture.completedFuture(null)
                }
            }
        }

        fun lettuceSyncClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): RedisCacheClient {
            return object : RedisCacheClient {
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
                    var counter = 0L
                    for (key in keys) {
                        counter += runBlocking { del(key) }
                    }
                    return counter
                }

                override fun flushAll() {
                    cache.clear()
                }
            }
        }
    }
}
