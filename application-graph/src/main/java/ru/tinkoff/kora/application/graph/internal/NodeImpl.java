package ru.tinkoff.kora.application.graph.internal;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.application.graph.Node;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NodeImpl<T> implements Node<T> {
    public final ApplicationGraphDraw graphDraw;
    public final int index;
    public final Graph.Factory<? extends T> factory;
    // leaks for the test purposes
    private final Type type;
    private final Class<?> tag;
    private final List<NodeImpl<?>> dependencyNodes;
    private final List<NodeImpl<? extends GraphInterceptor<T>>> interceptors;
    private final List<NodeImpl<?>> intercepts;
    private final List<NodeImpl<?>> dependentNodes;
    private final boolean isValueOf;

    public NodeImpl(ApplicationGraphDraw graphDraw, int index, Graph.Factory<? extends T> factory, Type type, List<NodeImpl<?>> dependencyNodes, List<NodeImpl<? extends GraphInterceptor<T>>> interceptors, @Nullable Class<?> tag) {
        this.graphDraw = graphDraw;
        this.index = index;
        this.factory = factory;
        this.type = type;
        this.dependencyNodes = List.copyOf(dependencyNodes);
        this.dependentNodes = new ArrayList<>();
        this.interceptors = List.copyOf(interceptors);
        this.intercepts = new ArrayList<>();
        this.isValueOf = false;
        this.tag = tag;
    }

    private NodeImpl(ApplicationGraphDraw graphDraw, int index, Graph.Factory<? extends T> factory, Type type, List<NodeImpl<?>> dependencyNodes, List<NodeImpl<? extends GraphInterceptor<T>>> interceptors, List<NodeImpl<?>> dependentNodes, List<NodeImpl<?>> intercepts, boolean isValueOf, @Nullable Class<?> tag) {
        this.graphDraw = graphDraw;
        this.index = index;
        this.factory = factory;
        this.type = type;
        this.dependencyNodes = List.copyOf(dependencyNodes);
        this.interceptors = List.copyOf(interceptors);
        this.dependentNodes = dependentNodes;
        this.intercepts = intercepts;
        this.isValueOf = isValueOf;
        this.tag = tag;
    }

    @Override
    public Node<T> valueOf() {
        return new NodeImpl<>(this.graphDraw, this.index, this.factory, this.type, this.dependencyNodes, this.interceptors, this.dependentNodes, this.intercepts, true, this.tag);
    }

    public void addDependentNode(NodeImpl<?> node) {
        this.dependentNodes.add(node);
    }

    public void deleteDependentNode(NodeImpl<?> node) {
        this.dependentNodes.remove(node);
    }

    public void intercepts(NodeImpl<?> node) {
        this.intercepts.add(node);
    }

    public List<NodeImpl<?>> getDependentNodes() {
        return Collections.unmodifiableList(this.dependentNodes);
    }

    public List<NodeImpl<?>> getDependencyNodes() {
        return this.dependencyNodes;
    }

    public List<NodeImpl<? extends GraphInterceptor<T>>> getInterceptors() {
        return List.copyOf(this.interceptors);
    }

    public List<NodeImpl<?>> getIntercepts() {
        return Collections.unmodifiableList(this.intercepts);
    }

    @Override
    public boolean isValueOf() {
        return this.isValueOf;
    }

    @Override
    public Type type() {
        return this.type;
    }

    @Override
    public Class<?> tag() {
        return tag;
    }
}
