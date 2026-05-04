@NullMarked
module kora.cache.redis.lettuce {
    requires transitive kora.cache.common;
    requires transitive kora.redis.lettuce;
    requires kora.common;
    requires kora.telemetry.common;

    exports io.koraframework.cache.redis;
    exports io.koraframework.cache.redis.lettuce;
    exports io.koraframework.cache.redis.telemetry;
}
