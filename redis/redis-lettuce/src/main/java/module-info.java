import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires transitive io.netty.transport;
    requires transitive io.netty.common;

    requires transitive kora.common;
    requires transitive kora.config.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.netty.common;
    requires transitive lettuce.core;

    exports io.koraframework.redis.lettuce;
    exports io.koraframework.redis.lettuce.telemetry;
}
