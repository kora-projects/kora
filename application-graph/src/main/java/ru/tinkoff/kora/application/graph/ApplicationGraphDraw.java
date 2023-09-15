package ru.tinkoff.kora.application.graph;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.internal.GraphImpl;
import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ApplicationGraphDraw {

    private final List<NodeImpl<?>> graphNodes = new ArrayList<>();
    private final Class<?> root;

    public ApplicationGraphDraw(Class<?> root) {
        this.root = root;
    }

    public Class<?> getRoot() {
        return root;
    }

    public <T> Node<T> addNode0(Type type, Class<?>[] tags, Graph.Factory<? extends T> factory, Node<?>... dependencies) {
        return this.addNode0(type, tags, factory, List.of(), dependencies);
    }

    public <T> Node<T> addNode0(Type type, Class<?>[] tags, Graph.Factory<? extends T> factory, List<? extends Node<? extends GraphInterceptor<T>>> interceptors, Node<?>... dependencies) {
        var dependenciesList = new ArrayList<NodeImpl<?>>();
        for (var dependency : dependencies) {
            dependenciesList.add((NodeImpl<?>) dependency);
        }
        var interceptorsList = new ArrayList<NodeImpl<? extends GraphInterceptor<T>>>();
        for (var interceptor : interceptors) {
            interceptorsList.add((NodeImpl<? extends GraphInterceptor<T>>) interceptor);
        }
        for (var dependency : dependencies) {
            var node = (NodeImpl<?>) dependency;
            if (node.index >= 0 && node.graphDraw != this) {
                throw new IllegalArgumentException("Dependency is from another graph");
            }
        }

        var node = new NodeImpl<>(this, this.graphNodes.size(), factory, type, dependenciesList, interceptorsList, tags);
        this.graphNodes.add(node);
        for (var dependency : dependenciesList) {
            if (dependency.isValueOf()) {
                dependency.addDependentNode((NodeImpl<?>) node.valueOf());
            } else {
                dependency.addDependentNode(node);
            }
        }
        for (var interceptor : interceptors) {
            var n = (NodeImpl<?>) interceptor;
            n.intercepts(node);
        }

        return node;
    }

    public RefreshableGraph init() {
        var graph = new GraphImpl(this);
        graph.init();
        return graph;
    }

    public List<Node<?>> getNodes() {
        return Collections.unmodifiableList(this.graphNodes);
    }

    public int size() {
        return this.graphNodes.size();
    }

    @Nullable
    public Node<?> findNodeByType(Type type) {
        for (var graphNode : this.graphNodes) {
            if (graphNode.type().equals(type) && graphNode.tags().length == 0) {
                return graphNode;
            }
        }
        return null;
    }

    public List<Node<?>> findNodesByType(Type type, Class<?>[] tags) {
        var result = new ArrayList<Node<?>>();
        for (var graphNode : this.graphNodes) {
            if (graphNode.type().equals(type)) {
                if (tags.length == 0 && graphNode.tags().length == 0) {
                    result.add(graphNode);
                } else if (tags.length == 1 && tags[0].getCanonicalName().equals("ru.tinkoff.kora.common.Tag.Any")) {
                    result.add(graphNode);
                } else if (Arrays.equals(tags, graphNode.tags()) && graphNode.type().equals(type)) {
                    result.add(graphNode);
                }
            }
        }
        return result;
    }

    public <T> void replaceNode(Node<T> node, Graph.Factory<? extends T> factory) {
        var casted = (NodeImpl<T>) node;
        this.graphNodes.set(casted.index, new NodeImpl<T>(
            this, casted.index, factory, node.type(), List.of(), List.of(), node.tags()
        ));
        for (var graphNode : graphNodes) {
            graphNode.deleteDependentNode(casted);
        }
    }

    public ApplicationGraphDraw copy() {
        var draw = new ApplicationGraphDraw(this.root);
        for (var node : this.graphNodes) {
            class T {
                static <T> void addNode(ApplicationGraphDraw draw, NodeImpl<T> node) {
                    var dependencies = new NodeImpl<?>[node.getDependencyNodes().size()];
                    for (int i = 0; i < dependencies.length; i++) {
                        var dependency = node.getDependencyNodes().get(i);
                        dependencies[i] = draw.graphNodes.get(dependency.index);
                    }
                    draw.addNode0(node.type(), node.tags(), node.factory, node.getInterceptors(), dependencies);
                }
            }
            T.addNode(draw, node);
        }
        return draw;
    }

    public ApplicationGraphDraw subgraph(List<Node<?>> excludeTransitive, Iterable<Node<?>> rootNodes) {
        var seen = new TreeMap<Integer, Integer>();
        var excludeTransitiveSet = excludeTransitive.stream().map(n -> ((NodeImpl<?>) n).index).collect(Collectors.toSet());

        var subgraph = new ApplicationGraphDraw(this.root);
        var visitor = new Object() {
            public <T> Node<T> accept(NodeImpl<T> node) {
                if (!seen.containsKey(node.index)) {
                    var dependencies = new ArrayList<Node<?>>();
                    var interceptors = new ArrayList<Node<? extends GraphInterceptor<T>>>();
                    if (!excludeTransitiveSet.contains(node.index)) {
                        for (var dependencyNode : node.getDependencyNodes()) {
                            dependencies.add(this.accept(dependencyNode));
                        }
                    }
                    for (var interceptor : node.getInterceptors()) {
                        interceptors.add(this.accept(interceptor));
                    }
                    Graph.Factory<T> factory = graph -> node.factory.get(new Graph() {
                        @Override
                        public ApplicationGraphDraw draw() {
                            return subgraph;
                        }

                        @Override
                        public <Q> Q get(Node<Q> node1) {
                            var casted = (NodeImpl<Q>) node1;
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(casted.index));
                            return graph.get(realNode);
                        }

                        @Override
                        public <Q> ValueOf<Q> valueOf(Node<? extends Q> node1) {
                            var casted = (NodeImpl<? extends Q>) node1;
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(casted.index));
                            return graph.valueOf(realNode);
                        }

                        @Override
                        public <Q> PromiseOf<Q> promiseOf(Node<Q> node1) {
                            var casted = (NodeImpl<Q>) node1;
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(casted.index));
                            return graph.promiseOf(realNode);
                        }
                    });
                    var newNode = (NodeImpl<T>) subgraph.addNode0(node.type(), node.tags(), factory, interceptors, dependencies.toArray(new Node<?>[0]));
                    seen.put(node.index, newNode.index);
                    return newNode;
                }
                var index = seen.get(node.index);
                @SuppressWarnings("unchecked")
                var newNode = (Node<T>) subgraph.graphNodes.get(index);
                return newNode;
            }
        };
        for (var rootNode : rootNodes) {
            var casted = (NodeImpl<?>) rootNode;
            visitor.accept(this.graphNodes.get(casted.index));
        }
        return subgraph;
    }
}
