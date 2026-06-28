import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.scheduling.jdk {
    requires transitive kora.scheduling.common;
    requires transitive kora.config.common;

    exports io.koraframework.scheduling.jdk;
    exports io.koraframework.scheduling.jdk.annotation;
}
