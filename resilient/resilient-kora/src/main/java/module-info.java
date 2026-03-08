import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.resilent.kora {
    requires transitive kora.common;
    requires transitive kora.config.common;
    requires static org.jetbrains.annotations;

    exports io.koraframework.resilient;
    exports io.koraframework.resilient.circuitbreaker;
    exports io.koraframework.resilient.circuitbreaker.annotation;
    exports io.koraframework.resilient.fallback;
    exports io.koraframework.resilient.fallback.annotation;
    exports io.koraframework.resilient.retry;
    exports io.koraframework.resilient.retry.annotation;
    exports io.koraframework.resilient.timeout;
    exports io.koraframework.resilient.timeout.annotation;
}
