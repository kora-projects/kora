package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Type;

record GraphCandidate(@Nonnull Type type, @Nullable Class<?> tag) {

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
