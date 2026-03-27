package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.NodeWithMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public final class AllSimpleImpl<T> implements All<T> {
    private final ArrayList<T> values;

    @SafeVarargs
    public AllSimpleImpl(Graph graph, NodeWithMapper<?, T>... nodes) {
        this.values = new ArrayList<T>(nodes.length);
        for (var node : nodes) {
            this.values.add(get(graph, node));
        }
    }

    private static <N, V> V get(Graph graph, NodeWithMapper<N, V> node) {
        return node.mapper().apply(graph.get(node.node()));
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableCollection(this.values).iterator();
    }
}
