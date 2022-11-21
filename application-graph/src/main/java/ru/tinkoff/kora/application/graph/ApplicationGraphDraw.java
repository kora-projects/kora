package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApplicationGraphDraw {
    private final List<Node<?>> graphNodes = new ArrayList<>();
    private final Class<?> root;

    public ApplicationGraphDraw(Class<?> root) {
        this.root = root;
    }

    public Class<?> getRoot() {
        return root;
    }

    public <T> Node<T> addNode0(Class<?>[] tags, Graph.Factory<T> factory, Node<?>... dependencies) {
        return this.addNode0(tags, factory, List.of(), dependencies);
    }

    public <T> Node<T> addNode0(Class<?>[] tags, Graph.Factory<T> factory, List<Node<? extends GraphInterceptor<T>>> interceptors, Node<?>... dependencies) {
        for (var dependency : dependencies) {
            if (dependency.index >= 0 && dependency.graphDraw != this) {
                throw new IllegalArgumentException("Dependency is from another graph");
            }
        }

        var node = new Node<>(this, this.graphNodes.size(), factory, List.of(dependencies), List.copyOf(interceptors), tags);
        this.graphNodes.add(node);
        for (var dependency : dependencies) {
            if (dependency.isValueOf()) {
                dependency.addDependentNode(node.valueOf());
            } else {
                dependency.addDependentNode(node);
            }
        }
        for (var interceptor : interceptors) {
            interceptor.intercepts(node);
        }

        return node;
    }

    public Mono<RefreshableGraph> init() {
        return Mono.defer(() -> {
            var graph = new GraphImpl(this);
            return graph.init().thenReturn(graph);
        });
    }

    public List<Node<?>> getNodes() {
        return Collections.unmodifiableList(this.graphNodes);
    }

    public int size() {
        return this.graphNodes.size();
    }

}
