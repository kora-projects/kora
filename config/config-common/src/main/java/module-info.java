import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.config.common {
    requires transitive kora.common;
    requires transitive org.slf4j;

    exports io.koraframework.config.common;
    exports io.koraframework.config.common.annotation;
    exports io.koraframework.config.common.mapper;
    exports io.koraframework.config.common.origin;
    exports io.koraframework.config.common.impl;
    exports io.koraframework.config.common.util;
    exports io.koraframework.config.common.exception;
}
