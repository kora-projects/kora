package ru.tinkoff.kora.application.graph.internal;

import ru.tinkoff.kora.application.graph.All;

import java.util.ArrayList;
import java.util.List;

public final class AllImpl<T> extends ArrayList<T> implements All<T> {
    @SafeVarargs
    @SuppressWarnings("varargs")
    public AllImpl(T... values) {
        super(List.of(values));
    }

    public AllImpl(List<T> values) {
        super(List.copyOf(values));
    }
}
