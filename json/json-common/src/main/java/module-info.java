import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.json.common {
    requires transitive tools.jackson.core;
    requires transitive com.alibaba.fastjson2;
    requires transitive kora.common;
    requires static kotlin.stdlib;
    requires org.jctools.core;

    exports io.koraframework.json.common;
    exports io.koraframework.json.common.annotation;
    exports io.koraframework.json.common.util;
    exports io.koraframework.json.common.writer;
    exports io.koraframework.json.common.reader;
}
