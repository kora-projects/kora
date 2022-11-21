package ru.tinkoff.kora.cache.symbol.processor.testcache

import reactor.core.publisher.Mono
import ru.tinkoff.kora.cache.Cache
import ru.tinkoff.kora.cache.LoadableCache

class DummyCache<K, V>(private val name: String) : Cache<K, V>, LoadableCache<K, V> {

    private val cache: MutableMap<String, V> = HashMap()

    fun origin(): String {
        return "dummy";
    }

    fun name(): String {
        return name
    }

    override operator fun get(key: K): V? {
        return cache[key.toString()]
    }

    override fun put(key: K, value: V): V {
        cache[key.toString()] = value
        return value
    }

    override fun invalidate(key: K) {
        cache.remove(key.toString())
    }

    override fun invalidateAll() {
        cache.clear()
    }

    override fun getAsync(key: K): Mono<V> {
        return Mono.justOrEmpty(get(key))
    }

    override fun putAsync(key: K, value: V): Mono<V> {
        put(key, value)
        return Mono.just(value)
    }

    override fun invalidateAsync(key: K): Mono<Void> {
        invalidate(key)
        return Mono.empty()
    }

    override fun invalidateAllAsync(): Mono<Void> {
        invalidateAll()
        return Mono.empty()
    }

    fun isEmpty(): Boolean {
        return cache.isEmpty()
    }

    fun reset() {
        cache.clear()
    }
}
