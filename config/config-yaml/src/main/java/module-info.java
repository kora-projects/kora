import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.config.yaml {
    requires transitive kora.config.common;
    requires transitive org.snakeyaml.engine;

    exports io.koraframework.config.yaml;
}
