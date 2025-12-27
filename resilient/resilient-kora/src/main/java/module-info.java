import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.resilent.kora {
    requires transitive kora.common;
    requires kora.config.common;
    requires static org.jetbrains.annotations;

    exports ru.tinkoff.kora.resilient;
    exports ru.tinkoff.kora.resilient.circuitbreaker;
    exports ru.tinkoff.kora.resilient.circuitbreaker.annotation;
    exports ru.tinkoff.kora.resilient.fallback;
    exports ru.tinkoff.kora.resilient.fallback.annotation;
    exports ru.tinkoff.kora.resilient.retry;
    exports ru.tinkoff.kora.resilient.retry.annotation;
    exports ru.tinkoff.kora.resilient.timeout;
    exports ru.tinkoff.kora.resilient.timeout.annotation;
}
