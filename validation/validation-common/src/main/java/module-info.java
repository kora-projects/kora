module kora.validation.common {
    requires transitive kora.common;
    requires static org.jetbrains.annotations;
    requires kora.config.common;

    exports ru.tinkoff.kora.validation.common;
    exports ru.tinkoff.kora.validation.common.annotation;
    exports ru.tinkoff.kora.validation.common.constraint.factory;
}
