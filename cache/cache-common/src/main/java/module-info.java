import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.cache.common {
    requires transitive kora.common;

    exports io.koraframework.cache;
    exports io.koraframework.cache.annotation;
}
