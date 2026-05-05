import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires transitive io.netty.transport;
    requires transitive io.netty.common;

    requires kora.common;
    requires kora.config.common;
    requires kora.telemetry.common;
    requires kora.cache.common;
    requires kora.redis.lettuce;
    requires kora.json.common;
    requires lettuce.core;

    exports io.koraframework.cache.redis;
    exports io.koraframework.cache.redis.lettuce;
    exports io.koraframework.cache.redis.telemetry;
}
