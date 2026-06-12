import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires transitive io.netty.transport;
    requires transitive io.netty.common;

    requires kora.common;
    requires kora.config.common;
    requires kora.telemetry.common;
    requires kora.netty.common;
    requires lettuce.core;

    exports io.koraframework.redis.lettuce;
    exports io.koraframework.redis.lettuce.telemetry;
}
