import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.json.common {
    requires transitive tools.jackson.core;
    requires transitive kora.common;
    requires static kotlin.stdlib;

    exports io.koraframework.json.common;
    exports io.koraframework.json.common.annotation;
    exports io.koraframework.json.common.util;
}
