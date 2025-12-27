module kora.logging.common {
    requires transitive kora.common;
    requires transitive kora.json.common;
    requires transitive kora.config.common;
    requires transitive org.slf4j;
    requires transitive jul.to.slf4j;

    exports ru.tinkoff.kora.logging.common;
    exports ru.tinkoff.kora.logging.common.annotation;
    exports ru.tinkoff.kora.logging.common.arg;
}
