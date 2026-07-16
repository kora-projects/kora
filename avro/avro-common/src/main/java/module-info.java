import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.avro.common {
    exports io.koraframework.avro.common;
    exports io.koraframework.avro.common.annotation;

    requires transitive kora.common;

    requires transitive org.apache.avro;
}
