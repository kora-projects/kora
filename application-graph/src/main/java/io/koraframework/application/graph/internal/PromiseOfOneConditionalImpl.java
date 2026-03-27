package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.NodeWithMapper;

import java.util.Optional;

public class PromiseOfOneConditionalImpl<N, V> extends BasePromiseOf<V> {
    private final NodeWithMapper<N, V>[] nodes;

    public PromiseOfOneConditionalImpl(NodeWithMapper<N, V>[] nodes) {
        this.nodes = nodes;
    }

    @Override
    public Optional<V> get() {
        var graph = this.graph;
        if (graph == null) {
            return Optional.empty();
        } else {
            return Optional.of(graph.getOneOf(nodes));
        }
    }
}
