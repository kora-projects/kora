package ru.tinkoff.kora.application.graph;

import ru.tinkoff.kora.application.graph.internal.AllImpl;

import java.util.List;

public sealed interface All<T> extends List<T> permits AllImpl {
    @SafeVarargs
    static <T> All<T> of(T... values) {
        return new AllImpl<>(values);
    }

    static <T> All<T> of(List<T> values) {
        return new AllImpl<>(values);
    }
}

