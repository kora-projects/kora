package ru.tinkoff.kora.application.graph;

import java.util.ArrayList;
import java.util.List;

public interface All<T> extends List<T> {
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<T> of(T... values) {
        return new AllImpl<>(values);
    }

    @SuppressWarnings("varargs")
    static <T> All<T> of(List<T> values) {
        return new AllImpl<>(values);
    }
}

final class AllImpl<T> extends ArrayList<T> implements All<T> {
    @SafeVarargs
    @SuppressWarnings("varargs")
    public AllImpl(T... values) {
        super(List.of(values));
    }

    @SuppressWarnings("varargs")
    public AllImpl(List<T> values) {
        super(List.copyOf(values));
    }
}

