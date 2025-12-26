import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.caffeine {
    requires transitive kora.cache.common;
    requires transitive com.github.benmanes.caffeine;
    requires transitive kora.config.common;

    exports ru.tinkoff.kora.cache.caffeine;
}
