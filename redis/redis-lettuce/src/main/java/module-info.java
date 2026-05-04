import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.redis.lettuce {
    requires static transitive io.netty.transport.unix.common;

    requires io.netty.transport;
    requires io.netty.common;
    requires lettuce.core;

    requires kora.telemetry.common;
    requires kora.netty.common;
    requires kora.common;

    exports io.koraframework.redis.lettuce;
    exports io.koraframework.redis.lettuce.telemetry;
}
