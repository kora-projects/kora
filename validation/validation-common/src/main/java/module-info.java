import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.validation.common {
    requires transitive kora.common;
    requires transitive kora.config.common;

    exports io.koraframework.validation.common;
    exports io.koraframework.validation.common.annotation;
    exports io.koraframework.validation.common.constraint;
    exports io.koraframework.validation.common.constraint.factory;
}
