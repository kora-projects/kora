package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ApplicationGraphDraw {

    private final List<Node<?>> graphNodes = new ArrayList<>();
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

    public <T> Node<T> addNode0(Type type, Class<?>[] tags, Graph.Factory<? extends T> factory, List<Node<? extends GraphInterceptor<T>>> interceptors, Node<?>... dependencies) {
        for (var dependency : dependencies) {
            if (dependency.index >= 0 && dependency.graphDraw != this) {
                throw new IllegalArgumentException("Dependency is from another graph");
            }
        }

        var node = new Node<>(this, this.graphNodes.size(), factory, type, List.of(dependencies), List.copyOf(interceptors), tags);
        this.graphNodes.add(node);
        for (var dependency : dependencies) {
            if (dependency.isValueOf()) {
                dependency.addDependentNode(node.valueOf());
            } else {
                dependency.addDependentNode(node);
            }
        }
        for (var interceptor : interceptors) {
            interceptor.intercepts(node);
        }

        return node;
    }

    public Mono<RefreshableGraph> init() {
        return Mono.defer(() -> {
            var graph = new GraphImpl(this);
            return graph.init().thenReturn(graph);
        });
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

    public <T> void replaceNode(Node<T> node, Graph.Factory<? extends T> factory) {
        this.graphNodes.set(node.index, new Node<T>(
            this, node.index, factory, node.type(), List.of(), List.of(), node.tags()
        ));
        for (var graphNode : graphNodes) {
            graphNode.deleteDependentNode(node);
        }
    }

    public ApplicationGraphDraw copy() {
        var draw = new ApplicationGraphDraw(this.root);
        for (var node : this.graphNodes) {
            class T {
                static <T> void addNode(ApplicationGraphDraw draw, Node<T> node) {
                    var dependencies = new Node<?>[node.getDependencyNodes().size()];
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
        var excludeTransitiveSet = excludeTransitive.stream().map(n -> n.index).collect(Collectors.toSet());

        var subgraph = new ApplicationGraphDraw(this.root);
        var visitor = new Object() {
            public <T> Node<T> accept(Node<T> node) {
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
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(node1.index));
                            return graph.get(realNode);
                        }

                        @Override
                        public <Q> ValueOf<Q> valueOf(Node<? extends Q> node1) {
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(node1.index));
                            return graph.valueOf(realNode);
                        }

                        @Override
                        public <Q> PromiseOf<Q> promiseOf(Node<Q> node1) {
                            @SuppressWarnings("unchecked")
                            var realNode = (Node<Q>) subgraph.graphNodes.get(seen.get(node1.index));
                            return graph.promiseOf(realNode);
                        }
                    });
                    var newNode = subgraph.addNode0(node.type(), node.tags(), factory, interceptors, dependencies.toArray(new Node<?>[0]));
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
            visitor.accept(this.graphNodes.get(rootNode.index));
        }
        return subgraph;
    }
}
