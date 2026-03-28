package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

public final class AllValuesImpl<T> implements All<ValueOf<T>> {
    private final Graph graph;
    private final ArrayList<ValueWrapper<?, T>> values;

    @SafeVarargs
    public AllValuesImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.graph = graph;
        this.values = new ArrayList<>(nodes.length);
        for (var node : nodes) {
            this.values.add(new ValueWrapper<>(graph, node));
        }
    }

    private record ValueWrapper<N, V>(ValueOf<? extends N> value, Function<N, V> mapper, Node<? extends N> node) {
        public ValueWrapper(Graph graph, NodeWithMapper<N, V> element) {
            var promise = graph.valueOf(element.node());
            this(promise, element.mapper(), element.node());
        }

        public ValueOf<V> get() {
            return value.map(mapper::apply);
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
}
