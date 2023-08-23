package ru.tinkoff.kora.cache.symbol.processor

import reactor.core.publisher.Mono
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.redis.RedisCacheConfig
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*

class CacheRunner {

    companion object {

        fun getCaffeineConfig() : CaffeineCacheConfig {
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
            }
        }

        fun getRedisConfig(): RedisCacheConfig? {
            return object : RedisCacheConfig {
                override fun expireAfterWrite(): Duration? {
                    return null
                }

                override fun expireAfterAccess(): Duration? {
                    return null
                }
            }
        }

        fun syncRedisClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): SyncRedisClient {
            return object : SyncRedisClient {
                override fun get(key: ByteArray): ByteArray? {
                    val r = cache[ByteBuffer.wrap(key)]
                    return r?.array()
                }

                override fun get(keys: Array<ByteArray>): Map<ByteArray, ByteArray> {
                    val result: MutableMap<ByteArray, ByteArray> = HashMap()
                    for (key in keys) {
                        Optional.ofNullable(cache[ByteBuffer.wrap(key)]).ifPresent { r: ByteBuffer ->
                            result[key] = r.array()
                        }
                    }
                    return result
                }

                override fun getExpire(key: ByteArray, expireAfterMillis: Long): ByteArray? {
                    return get(key)
                }

                override fun getExpire(keys: Array<ByteArray>, expireAfterMillis: Long): Map<ByteArray, ByteArray> {
                    return get(keys)
                }

                override fun set(key: ByteArray, value: ByteArray) {
                    cache[ByteBuffer.wrap(key)] = ByteBuffer.wrap(value)
                }

                override fun setExpire(key: ByteArray, value: ByteArray, expireAfterMillis: Long) {
                    set(key, value)
                }

                override fun del(key: ByteArray): Long {
                    return if (cache.remove(ByteBuffer.wrap(key)) == null) 0 else 1
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

        fun reactiveRedisClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): ReactiveRedisClient? {
            val syncRedisClient = syncRedisClient(cache)
            return object : ReactiveRedisClient {
                override fun get(key: ByteArray): Mono<ByteArray> {
                    return Mono.justOrEmpty(syncRedisClient[key])
                }

                override fun get(keys: Array<ByteArray>): Mono<Map<ByteArray, ByteArray>> {
                    return Mono.justOrEmpty(syncRedisClient[keys])
                }

                override fun getExpire(key: ByteArray, expireAfterMillis: Long): Mono<ByteArray> {
                    return get(key)
                }

                override fun getExpire(keys: Array<ByteArray>, expireAfterMillis: Long): Mono<Map<ByteArray, ByteArray>> {
                    return get(keys)
                }

                override fun set(key: ByteArray, value: ByteArray): Mono<Boolean> {
                    syncRedisClient[key] = value
                    return Mono.just(true)
                }

                override fun setExpire(key: ByteArray, value: ByteArray, expireAfterMillis: Long): Mono<Boolean> {
                    return set(key, value)
                }

                override fun del(key: ByteArray): Mono<Long> {
                    return Mono.justOrEmpty(syncRedisClient.del(key))
                }

                override fun del(keys: Array<ByteArray>): Mono<Long> {
                    return Mono.justOrEmpty(syncRedisClient.del(keys))
                }

                override fun flushAll(): Mono<Boolean> {
                    syncRedisClient.flushAll()
                    return Mono.just(true)
                }
            }
        }
    }
}
