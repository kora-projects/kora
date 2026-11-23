package io.koraframework.logging.logback.json.writer;

import tools.jackson.core.io.SerializedString;

import java.util.Map;

final class KeyNameCache {

    private static final int MAX_SIZE = 1024;

    private KeyNameCache() {}

    /**
     * Returns a cached serialized key name; once the cache is full, new names are serialized without caching, so dynamic
     * key names (per-request MDC keys, generated argument keys) cannot grow it without bound.
     */
    static SerializedString get(Map<String, SerializedString> cache, String key) {
        var name = cache.get(key);
        if (name != null) {
            return name;
        }
        name = new SerializedString(key);
        if (cache.size() < MAX_SIZE) {
            var existing = cache.putIfAbsent(key, name);
            if (existing != null) {
                return existing;
            }
        }
        return name;
    }
}
