import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.avro.common {
    exports io.koraframework.avro.common;
    exports io.koraframework.avro.common.annotation;
    exports io.koraframework.avro.common.reader;
    exports io.koraframework.avro.common.writer;

    requires transitive kora.common;

    requires transitive org.apache.avro;
}
