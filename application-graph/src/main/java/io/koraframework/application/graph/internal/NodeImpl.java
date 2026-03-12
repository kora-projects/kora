package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.GraphInterceptor;
import io.koraframework.application.graph.Node;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

public final class NodeImpl<T> implements Node<T> {
    public final ApplicationGraphDraw graphDraw;
    public final int index;
    public final Graph.Factory<? extends T> factory;

    public final Type type;
    @Nullable
    public final Class<?> tag;

    public final List<ApplicationGraphDraw.CreateDependency> createDependencies;
    public final List<Node<?>> refreshDependencies;
    public final List<Node<? extends GraphInterceptor<T>>> interceptors;

    public NodeImpl(
        ApplicationGraphDraw graphDraw,
        Type type,
        @Nullable Class<?> tag,
        int index,
        List<ApplicationGraphDraw.CreateDependency> createDependencies,
        List<Node<?>> refreshDependencies,
        List<Node<? extends GraphInterceptor<T>>> interceptors,
        Graph.Factory<? extends T> factory) {
        this.graphDraw = graphDraw;
        this.index = index;
        this.createDependencies = createDependencies;
        this.refreshDependencies = refreshDependencies;
        this.factory = factory;
        this.type = type;
        this.interceptors = List.copyOf(interceptors);
        this.tag = tag;
    }

    @Override
    public Type type() {
        return this.type;
    }

    @Override
    @Nullable
    public Class<?> tag() {
        return tag;
    }

    @Override
    public String toString() {
        return "" + index;
    }
}
