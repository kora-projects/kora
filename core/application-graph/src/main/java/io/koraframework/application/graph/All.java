package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.AllPromisesImpl;
import io.koraframework.application.graph.internal.AllSimpleImpl;
import io.koraframework.application.graph.internal.AllValuesImpl;

import java.util.Iterator;
import java.util.List;

public sealed interface All<T> extends Iterable<T> permits All.StaticAll, AllPromisesImpl, AllSimpleImpl, AllValuesImpl {
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<T> all(Graph graph, NodeWithMapper<?, T>... nodes) {
        return new AllSimpleImpl<>(graph, nodes);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<T> of(T... values) {
        return new StaticAll<>(List.of(values));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<ValueOf<T>> allValues(Graph graph, NodeWithMapper<?, T>... nodes) {
        return new AllValuesImpl<>(graph, nodes);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<PromiseOf<T>> allPromises(Graph graph, NodeWithMapper<?, T>... nodes) {
        return new AllPromisesImpl<>(graph, nodes);
    }

    final class StaticAll<T> implements All<T> {
        private final List<T> values;

        public StaticAll(List<T> values) {
            this.values = values;
        }

        @Override
        public Iterator<T> iterator() {
            return this.values.iterator();
        }
    }

}

