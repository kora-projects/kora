package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.Node;
import io.koraframework.application.graph.PromiseOf;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class PromiseOfImpl<T> implements PromiseOf<T> {
    @Nullable
    public Graph graph;
    private final Node<? extends T> node;

    public PromiseOfImpl(@Nullable Graph graph, NodeImpl<? extends T> node) {
        this.graph = graph;
        this.node = node;
    }

    @Override
    public Optional<T> get() {
        var graph = this.graph;
        if (graph == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(graph.get(this.node));
    }
}
