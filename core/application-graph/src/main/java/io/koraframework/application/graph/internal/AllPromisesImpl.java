package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;

import java.util.*;
import java.util.function.*;

public final class AllPromisesImpl<T> implements All<PromiseOf<T>> {
    private final Graph graph;
    private final PromiseWrapper<?, T>[] values;

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public AllPromisesImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.graph = graph;
        values = new PromiseWrapper[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            values[i] = new PromiseWrapper<>(graph, nodes[i]);
        }
    }

    @Override
    public Iterator<PromiseOf<T>> iterator() {
        return new AotFilteredIterator();
    }

    private static final class PromiseWrapper<N, V> {
        private final PromiseOf<Object> promise;
        private final Function<Object, Object> mapper;
        private final Node<? extends N> node;

        @SuppressWarnings("unchecked")
        public PromiseWrapper(Graph graph, NodeWithMapper<N, V> element) {
            promise = graph.promiseOf(element.node());
            mapper = (Function<Object, Object>) element.mapper();
            node = element.node();
        }

        public Node<? extends N> node() {
            return node;
        }

        @SuppressWarnings("unchecked")
        public PromiseOf<V> get() {
            if (mapper == null) {
                return (PromiseOf<V>) promise;
            }
            return (PromiseOf<V>) promise.map(mapper);
        }
    }

    private final class AotFilteredIterator implements Iterator<PromiseOf<T>> {
        private int currentIndex = 0;
        private PromiseOf<T> nextElement = null;
        private boolean hasCalculatedNext = false;

        @Override
        public boolean hasNext() {
            if (hasCalculatedNext) {
                return nextElement != null;
            }

            while (currentIndex < values.length) {
                var value = values[currentIndex++];
                var condition = value.node().condition();
                if (condition == null || condition.apply(graph) instanceof GraphCondition.ConditionResult.Matched) {
                    nextElement = value.get();
                    hasCalculatedNext = true;
                    return true;
                }
            }

            nextElement = null;
            hasCalculatedNext = true;
            return false;
        }

        @Override
        public PromiseOf<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            hasCalculatedNext = false;
            var element = nextElement;
            nextElement = null;
            return element;
        }
    }
}
