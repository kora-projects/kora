import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires transitive io.netty.transport;
    requires transitive io.netty.common;

    requires kora.cache.common;
    requires kora.redis.lettuce;
    requires lettuce.core;
    requires kora.common;
    requires kora.telemetry.common;
    requires kora.json.common;

    exports io.koraframework.cache.redis;
    exports io.koraframework.cache.redis.lettuce;
    exports io.koraframework.cache.redis.telemetry;
}
