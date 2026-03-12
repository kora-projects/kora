package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.AllPromisesImpl;
import io.koraframework.application.graph.internal.AllSimpleImpl;
import io.koraframework.application.graph.internal.AllValuesImpl;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public sealed interface All<T> extends Iterable<T> permits All.StaticAll, AllPromisesImpl, AllSimpleImpl, AllValuesImpl {
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<T> all(Graph graph, AllOfElement<?, T>... nodes) {
        return new AllSimpleImpl<>(graph, nodes);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<T> of(T... values) {
        return new StaticAll<>(List.of(values));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<ValueOf<T>> allValues(Graph graph, AllOfElement<?, T>... nodes) {
        return new AllValuesImpl<>(graph, nodes);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> All<PromiseOf<T>> allPromises(Graph graph, AllOfElement<?, T>... nodes) {
        return new AllPromisesImpl<>(graph, nodes);
    }

    record AllOfElement<N, V>(Node<? extends N> node, Function<N, V> mapper) {}

    static <T> AllOfElement<T, T> node(Node<? extends T> node) {
        return new AllOfElement<>(node, v -> v);
    }

    static <V, N extends Wrapped<V>> AllOfElement<N, V> unwrap(Node<? extends N> node) {
        return new AllOfElement<>(node, Wrapped::value);
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

