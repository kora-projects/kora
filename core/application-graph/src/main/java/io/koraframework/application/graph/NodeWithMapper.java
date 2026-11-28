package io.koraframework.application.graph;

import java.util.Objects;
import java.util.function.Function;

public final class NodeWithMapper<N, V> {

    private static final Function<?, ?> IDENTITY_MAPPER = new IdentityMapper<>();
    private static final Function<?, ?> UNWRAP_MAPPER = new UnwrapMapper<>();

    private final Node<? extends N> node;
    private final Function<N, V> mapper;

    public NodeWithMapper(Node<? extends N> node, Function<N, V> mapper) {
        this.node = node;
        this.mapper = mapper;
    }

    public Node<? extends N> node() {
        return node;
    }

    public Function<N, V> mapper() {
        return mapper;
    }

    @SuppressWarnings("unchecked")
    public static <T> NodeWithMapper<T, T> node(Node<? extends T> node) {
        return new NodeWithMapper<>(node, (Function<T, T>) IDENTITY_MAPPER);
    }

    @SuppressWarnings("unchecked")
    public static <V, N extends Wrapped<V>> NodeWithMapper<N, V> unwrap(Node<? extends N> node) {
        return new NodeWithMapper<>(node, (Function<N, V>) UNWRAP_MAPPER);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof NodeWithMapper) {
            var other = (NodeWithMapper<?, ?>) obj;
            return Objects.equals(node, other.node) && Objects.equals(mapper, other.mapper);
        }
        return false;
    }

    @Override
    public int hashCode() {
        var h = 31 + (node == null ? 0 : node.hashCode());
        return 31 * h + (mapper == null ? 0 : mapper.hashCode());
    }

    @Override
    public String toString() {
        return node.toString();
    }

    private static final class IdentityMapper<T> implements Function<T, T> {
        @Override
        public T apply(T t) {
            return t;
        }
    }

    private static final class UnwrapMapper<V, N extends Wrapped<V>> implements Function<N, V> {
        @Override
        public V apply(N v) {
            return v.value();
        }
    }
}
