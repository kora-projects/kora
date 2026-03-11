module kora.logging.common {
    requires transitive kora.common;
    requires transitive kora.json.common;
    requires transitive kora.config.common;
    requires transitive org.slf4j;
    requires transitive jul.to.slf4j;

    exports io.koraframework.logging.common;
    exports io.koraframework.logging.common.annotation;
    exports io.koraframework.logging.common.arg;
}
