package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.NodeWithMapper;
import io.koraframework.application.graph.ValueOf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

public final class AllValuesImpl<T> implements All<ValueOf<T>> {
    private final ArrayList<ValueWrapper<?, T>> values;

    @SafeVarargs
    public AllValuesImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.values = new ArrayList<>(nodes.length);
        for (var node : nodes) {
            this.values.add(new ValueWrapper<>(graph, node));
        }
    }

    private record ValueWrapper<N, V>(ValueOf<? extends N> value, Function<N, V> mapper) {
        public ValueWrapper(Graph graph, NodeWithMapper<N, V> element) {
            var promise = graph.valueOf(element.node());
            this(promise, element.mapper());
        }

        public ValueOf<V> get() {
            return value.map(mapper::apply);
        }
    }

    @Override
    public Iterator<ValueOf<T>> iterator() {
        var it = this.values.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public ValueOf<T> next() {
                return it.next().get();
            }
        };
    }
}
