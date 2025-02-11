module kora.json.common {
    requires transitive kora.common;
    requires transitive com.fasterxml.jackson.core;
    requires static kotlin.stdlib;

    exports ru.tinkoff.kora.json.common;
    exports ru.tinkoff.kora.json.common.annotation;
    exports ru.tinkoff.kora.json.common.util;
}
