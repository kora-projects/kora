package ru.tinkoff.kora.cache.annotation.processor;

import javax.annotation.Nullable;

/**
 * Please add Description Here.
 */
public record CacheOperation(CacheMeta meta, Key key, Value value) {

    public record Value(@Nullable String canonicalName) {}

    public record Key(String packageName, String simpleName) {

        public String canonicalName() {
            return (packageName.isEmpty())
                ? simpleName
                : packageName + "." + simpleName;
        }
    }

}
