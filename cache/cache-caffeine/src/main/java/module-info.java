import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.caffeine {
    requires transitive com.github.benmanes.caffeine;

    requires kora.common;
    requires kora.config.common;
    requires kora.telemetry.common;
    requires kora.cache.common;

    exports io.koraframework.cache.caffeine;
}
