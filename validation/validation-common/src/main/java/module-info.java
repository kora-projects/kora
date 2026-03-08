module kora.validation.common {
    requires transitive kora.common;
    requires static org.jetbrains.annotations;
    requires kora.config.common;

    exports io.koraframework.validation.common;
    exports io.koraframework.validation.common.annotation;
    exports io.koraframework.validation.common.constraint.factory;
}
