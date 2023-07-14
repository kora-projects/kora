package ru.tinkoff.kora.application.graph;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Node<T> {
    final ApplicationGraphDraw graphDraw;
    final int index;
    final Graph.Factory<? extends T> factory;
    // leaks for the test purposes
    private final Type type;
    private final Class<?>[] tags;
    private final List<Node<?>> dependencyNodes;
    private final List<Node<? extends GraphInterceptor<T>>> interceptors;
    private final List<Node<?>> intercepts;
    private final List<Node<?>> dependentNodes;
    private final boolean isValueOf;

    Node(ApplicationGraphDraw graphDraw, int index, Graph.Factory<? extends T> factory, Type type, List<Node<?>> dependencyNodes, List<Node<? extends GraphInterceptor<T>>> interceptors, Class<?>[] tags) {
        this.graphDraw = graphDraw;
        this.index = index;
        this.factory = factory;
        this.type = type;
        this.dependencyNodes = List.copyOf(dependencyNodes);
        this.dependentNodes = new ArrayList<>();
        this.interceptors = List.copyOf(interceptors);
        this.intercepts = new ArrayList<>();
        this.isValueOf = false;
        this.tags = tags;
    }

    private Node(ApplicationGraphDraw graphDraw, int index, Graph.Factory<? extends T> factory, Type type, List<Node<?>> dependencyNodes, List<Node<? extends GraphInterceptor<T>>> interceptors, List<Node<?>> dependentNodes, List<Node<?>> intercepts, boolean isValueOf, Class<?>[] tags) {
        this.graphDraw = graphDraw;
        this.index = index;
        this.factory = factory;
        this.type = type;
        this.dependencyNodes = List.copyOf(dependencyNodes);
        this.interceptors = List.copyOf(interceptors);
        this.dependentNodes = dependentNodes;
        this.intercepts = intercepts;
        this.isValueOf = isValueOf;
        this.tags = tags;
    }

    public Node<T> valueOf() {
        return new Node<>(this.graphDraw, this.index, this.factory, this.type, this.dependencyNodes, this.interceptors, this.dependentNodes, this.intercepts, true, this.tags);
    }

    void addDependentNode(Node<?> node) {
        this.dependentNodes.add(node);
    }

    public void deleteDependentNode(Node<?> node) {
        this.dependentNodes.remove(node);
    }

    void intercepts(Node<?> node) {
        this.intercepts.add(node);
    }

    public List<Node<?>> getDependentNodes() {
        return Collections.unmodifiableList(this.dependentNodes);
    }

    List<Node<?>> getDependencyNodes() {
        return this.dependencyNodes;
    }

    public List<Node<? extends GraphInterceptor<T>>> getInterceptors() {
        return List.copyOf(this.interceptors);
    }

    public List<Node<?>> getIntercepts() {
        return Collections.unmodifiableList(this.intercepts);
    }

    boolean isValueOf() {
        return this.isValueOf;
    }

    public Type type() {
        return this.type;
    }

    public Class<?>[] tags() {
        return tags;
    }
}
