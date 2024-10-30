package ru.tinkoff.kora.json.common;

import jakarta.annotation.Nullable;

record JsonNullableContainer<T>(boolean isDefined, boolean isNullable, @Nullable T value) implements JsonNullable<T> {

    static final JsonNullable NULLABLE = new JsonNullableContainer(true, true, null);
    static final JsonNullable UNDEFINED = new JsonNullableContainer(false, true, null);
}
