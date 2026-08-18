import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.resilent.distributed.kora {
    requires transitive kora.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;
    requires kora.resilent.kora;

    exports io.koraframework.resilient.distributed.ratelimiter;
    exports io.koraframework.resilient.distributed.retry;
}
