import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.redis.lettuce {
    requires kora.telemetry.common;
    requires kora.common;

    exports io.koraframework.redis.lettuce;
    exports io.koraframework.redis.lettuce.telemetry;
}
