import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires transitive io.netty.transport;
    requires transitive io.netty.common;

    requires transitive kora.common;
    requires transitive kora.config.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.cache.common;
    requires transitive kora.redis.lettuce;
    requires transitive kora.json.common;
    requires transitive lettuce.core;

    exports io.koraframework.cache.redis;
    exports io.koraframework.cache.redis.lettuce;
    exports io.koraframework.cache.redis.telemetry;
    exports io.koraframework.cache.redis.telemetry.impl;
}
