package ru.tinkoff.kora.application.graph.internal;

import ru.tinkoff.kora.application.graph.All;

import java.util.ArrayList;
import java.util.List;

public final class AllImpl<T> extends ArrayList<T> implements All<T> {
    public AllImpl(List<T> values) {
        super(values);
    }
}
