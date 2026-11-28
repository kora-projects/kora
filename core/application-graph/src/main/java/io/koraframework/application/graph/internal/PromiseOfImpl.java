package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.Node;
import java.util.Optional;

public class PromiseOfImpl<T> extends BasePromiseOf<T> {
    private final Node<? extends T> node;

    public PromiseOfImpl(NodeImpl<? extends T> node) {
        this.node = node;
    }

    @Override
    public Optional<T> get() {
        var g = graph;
        if (g == null) {
            return Optional.empty();
        }
        return Optional.of(g.get(node));
    }
}
