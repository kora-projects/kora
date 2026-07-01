package io.koraframework.application.graph;

import java.util.function.Function;

public record NodeWithMapper<N, V>(Node<? extends N> node, Function<N, V> mapper) {
    public static <T> NodeWithMapper<T, T> node(Node<? extends T> node) {
        return new NodeWithMapper<>(node, v -> v);
    }

    public static <V, N extends Wrapped<V>> NodeWithMapper<N, V> unwrap(Node<? extends N> node) {
        return new NodeWithMapper<>(node, Wrapped::value);
    }

    @Override
    public String toString() {
        return node.toString();
    }
}
