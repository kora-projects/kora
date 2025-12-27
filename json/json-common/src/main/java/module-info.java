import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.json.common {
    requires transitive tools.jackson.core;
    requires transitive kora.common;
    requires static kotlin.stdlib;

    exports ru.tinkoff.kora.json.common;
    exports ru.tinkoff.kora.json.common.annotation;
    exports ru.tinkoff.kora.json.common.util;
}
