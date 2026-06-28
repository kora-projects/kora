import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.config.hocon {
    requires transitive kora.config.common;
    requires transitive typesafe.config;

    exports io.koraframework.config.hocon;
}
