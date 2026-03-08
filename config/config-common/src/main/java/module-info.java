import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.config.common {
    requires transitive kora.common;
    requires transitive org.slf4j;

    exports io.koraframework.config.common;
    exports io.koraframework.config.common.annotation;
    exports io.koraframework.config.common.extractor;
    exports io.koraframework.config.common.factory;
    exports io.koraframework.config.common.origin;
}
