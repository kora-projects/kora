import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.config.hocon {
    requires transitive kora.config.common;

    exports io.koraframework.config.aot;
}
