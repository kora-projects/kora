package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.PromiseOf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

public final class AllPromisesImpl<T> implements All<PromiseOf<T>> {
    private final ArrayList<PromiseWrapper<?, T>> values;

    @SafeVarargs
    public AllPromisesImpl(Graph graph, AllOfElement<?, T>... nodes) {
        this.values = new ArrayList<>(nodes.length);
        for (var node : nodes) {
            this.values.add(new PromiseWrapper<>(graph, node));
        }
    }

    private record PromiseWrapper<N, V>(PromiseOf<? extends N> promise, Function<N, V> mapper) {
        public PromiseWrapper(Graph graph, AllOfElement<N, V> element) {
            var promise = graph.promiseOf(element.node());
            this(promise, element.mapper());
        }

        public PromiseOf<V> get() {
            return promise.map(mapper::apply);
        }
    }

    @Override
    public Iterator<PromiseOf<T>> iterator() {
        var it = values.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public PromiseOf<T> next() {
                return it.next().get();
            }
        };
    }

}
