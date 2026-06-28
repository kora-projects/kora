import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.resilent.kora {
    requires transitive kora.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;

    exports io.koraframework.resilient;
    exports io.koraframework.resilient.circuitbreaker;
    exports io.koraframework.resilient.circuitbreaker.annotation;
    exports io.koraframework.resilient.circuitbreaker.telemetry;
    exports io.koraframework.resilient.circuitbreaker.telemetry.impl;
    exports io.koraframework.resilient.fallback;
    exports io.koraframework.resilient.fallback.annotation;
    exports io.koraframework.resilient.fallback.telemetry;
    exports io.koraframework.resilient.fallback.telemetry.impl;
    exports io.koraframework.resilient.ratelimiter;
    exports io.koraframework.resilient.ratelimiter.annotation;
    exports io.koraframework.resilient.ratelimiter.telemetry;
    exports io.koraframework.resilient.ratelimiter.telemetry.impl;
    exports io.koraframework.resilient.retry;
    exports io.koraframework.resilient.retry.annotation;
    exports io.koraframework.resilient.retry.telemetry;
    exports io.koraframework.resilient.retry.telemetry.impl;
    exports io.koraframework.resilient.timeout;
    exports io.koraframework.resilient.timeout.annotation;
    exports io.koraframework.resilient.timeout.telemetry;
    exports io.koraframework.resilient.timeout.telemetry.impl;
}
