import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.json.common {
    requires transitive com.alibaba.fastjson2;
    requires transitive kora.common;
    requires static tools.jackson.core;
    requires static kotlin.stdlib;

    exports io.koraframework.json.common;
    exports io.koraframework.json.common.annotation;
    exports io.koraframework.json.common.util;
    exports io.koraframework.json.common.jackson.writer;
    exports io.koraframework.json.common.jackson.reader;
}
