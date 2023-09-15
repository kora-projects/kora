package ru.tinkoff.kora.application.graph.internal;

import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.application.graph.PromiseOf;

import java.util.Optional;

public class PromiseOfImpl<T> implements PromiseOf<T> {
    public Graph graph;
    private final NodeImpl<T> node;

    public PromiseOfImpl(Graph graph, NodeImpl<T> node) {
        this.graph = graph;
        this.node = node;
    }

    @Override
    public Optional<T> get() {
        if (this.graph == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.graph.get(this.node));
    }
}
