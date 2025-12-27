module kora.config.common {
    requires transitive kora.common;
    requires transitive org.slf4j;

    exports ru.tinkoff.kora.config.common;
    exports ru.tinkoff.kora.config.common.annotation;
    exports ru.tinkoff.kora.config.common.extractor;
    exports ru.tinkoff.kora.config.common.factory;
    exports ru.tinkoff.kora.config.common.origin;
}
