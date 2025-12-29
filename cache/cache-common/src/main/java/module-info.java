import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.common {
    requires transitive kora.common;

    exports ru.tinkoff.kora.cache;
    exports ru.tinkoff.kora.cache.annotation;
    exports ru.tinkoff.kora.cache.telemetry;
}
