package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

public final class AllPromisesImpl<T> implements All<PromiseOf<T>> {
    private final Graph graph;
    private final ArrayList<PromiseWrapper<?, T>> values;

    @SafeVarargs
    public AllPromisesImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.graph = graph;
        this.values = new ArrayList<>(nodes.length);
        for (var node : nodes) {
            this.values.add(new PromiseWrapper<>(graph, node));
        }
    }

    private record PromiseWrapper<N, V>(PromiseOf<? extends N> promise, Function<N, V> mapper, Node<? extends N> node) {
        public PromiseWrapper(Graph graph, NodeWithMapper<N, V> element) {
            var promise = graph.promiseOf(element.node());
            this(promise, element.mapper(), element.node());
        }

        public PromiseOf<V> get() {
            return promise.map(mapper::apply);
        }
    }

    @Override
    public Iterator<PromiseOf<T>> iterator() {
        var list = new ArrayList<PromiseOf<T>>();
        for (var value : this.values) {
            var condition = value.node().condition();
            if (condition == null || condition.apply(graph) instanceof GraphCondition.ConditionResult.Matched) {
                list.add(value.get());
            }
        }
        return list.iterator();
    }

}
