package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

public final class NodeImpl<T> implements Node<T> {
    public final ApplicationGraphDraw graphDraw;
    public final int index;
    public final Graph.Factory<? extends T> factory;

    public final Type type;
    @Nullable
    public final Class<?> tag;
    @Nullable
    public final Function<Graph, GraphCondition.ConditionResult> condition;

    public final List<ApplicationGraphDraw.CreateDependency> createDependencies;
    public final List<Node<?>> refreshDependencies;
    public final List<Node<? extends GraphInterceptor<T>>> interceptors;

    public NodeImpl(
        ApplicationGraphDraw graphDraw,
        Type type,
        @Nullable Class<?> tag,
        @Nullable Function<Graph, GraphCondition.ConditionResult> condition,
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
        this.condition = condition;
    }

    @Override
    public Type type() {
        return this.type;
    }

    @Override
    @Nullable
    public Class<?> tag() {
        return this.tag;
    }

    @Override
    @Nullable
    public Function<Graph, GraphCondition.ConditionResult> condition() {
        return this.condition;
    }

    @Override
    public String toString() {
        return "" + index;
    }
}
