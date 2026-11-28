package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;

import java.util.*;
import java.util.function.*;

public final class AllValuesImpl<T> implements All<ValueOf<T>> {
    private final Graph graph;
    private final ValueWrapper<?, T>[] values;

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public AllValuesImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.graph = graph;
        values = new ValueWrapper[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            values[i] = new ValueWrapper<>(graph, nodes[i]);
        }
    }

    @Override
    public Iterator<ValueOf<T>> iterator() {
        var list = new ArrayList<ValueOf<T>>();
        for (var value : this.values) {
            var condition = value.node().condition();
            if (condition == null || condition.apply(graph) instanceof GraphCondition.ConditionResult.Matched) {
                list.add(value.get());
            }
        }
        return list.iterator();
    }

    private static final class ValueWrapper<N, V> {
        private final ValueOf<Object> value;
        private final Function<Object, Object> mapper;
        private final Node<? extends N> node;

        @SuppressWarnings("unchecked")
        public ValueWrapper(Graph graph, NodeWithMapper<N, V> element) {
            value = graph.valueOf(element.node());
            mapper = (Function<Object, Object>) element.mapper();
            node = element.node();
        }

        public Node<? extends N> node() {
            return node;
        }

        @SuppressWarnings("unchecked")
        public ValueOf<V> get() {
            if (mapper == null) {
                return (ValueOf<V>) value;
            }
            return (ValueOf<V>) value.map(mapper);
        }
    }
}
