package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental

@KspExperimental
data class CacheOperation(val meta: CacheMeta, val key: Key, val value: Value) {

    data class Key(val packageName: String, val simpleName: String) {
        fun canonicalName(): String = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    }

    data class Value(val canonicalName: String?)
}
