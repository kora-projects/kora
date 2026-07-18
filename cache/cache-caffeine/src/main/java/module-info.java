import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.caffeine {
    requires transitive com.github.benmanes.caffeine;

    requires transitive kora.common;
    requires transitive kora.config.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.cache.common;

    exports io.koraframework.cache.caffeine;
    exports io.koraframework.cache.caffeine.telemetry;
    exports io.koraframework.cache.caffeine.telemetry.impl;
}
