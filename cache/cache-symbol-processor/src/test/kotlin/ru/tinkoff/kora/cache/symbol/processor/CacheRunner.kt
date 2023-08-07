package ru.tinkoff.kora.cache.symbol.processor

import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import java.time.Duration

class CacheRunner {

    companion object {

        fun getConfig() : CaffeineCacheConfig {
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
    }
}
