import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.caffeine {
    requires transitive kora.cache.common;
    requires transitive com.github.benmanes.caffeine;
    requires kora.telemetry.common;

    exports io.koraframework.cache.caffeine;
}
