package ru.tinkoff.kora.application.graph;

import java.util.Optional;

class PromiseOfImpl<T> implements PromiseOf<T> {
    Graph graph;
    private final Node<T> node;

    public PromiseOfImpl(Graph graph, Node<T> node) {
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
