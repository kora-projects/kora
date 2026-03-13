package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.Node;

import java.util.Optional;

public class PromiseOfOneConditionalImpl<T> extends BasePromiseOf<T> {
    private final Node<? extends T>[] nodes;

    public PromiseOfOneConditionalImpl(Node<? extends T>[] nodes) {
        this.nodes = nodes;
    }

    @Override
    public Optional<T> get() {
        var graph = this.graph;
        if (graph == null) {
            return Optional.empty();
        } else {
            return Optional.of(graph.getOneOf(nodes));
        }
    }
}
