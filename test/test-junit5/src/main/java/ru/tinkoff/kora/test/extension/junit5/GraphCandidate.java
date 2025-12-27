package ru.tinkoff.kora.test.extension.junit5;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;

record GraphCandidate(Type type, @Nullable Class<?> tag) {

    GraphCandidate(Type type) {
        this(type, null);
    }

    @Override
    public String toString() {
        return tag == null
            ? type.toString()
            : "[type=" + type + ", tag=" + tag + ']';
    }
}
