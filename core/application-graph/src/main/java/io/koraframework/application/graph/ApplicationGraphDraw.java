package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.GraphImpl;
import io.koraframework.application.graph.internal.NodeImpl;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
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

    public <T> Node<T> addNode(
        Type type,
        @Nullable Class<?> tag,
        @Nullable Function<Graph, GraphCondition.ConditionResult> condition,
        List<Node<?>> createDependencies,
        List<Node<?>> refreshDependencies,
        List<Node<? extends GraphInterceptor<T>>> interceptors,
        Graph.Factory<? extends T> factory) {
        return this.addNode(type, tag, condition, createDependencies, refreshDependencies, List::of, interceptors, factory);
    }

    public <T> Node<T> addNode(
        Type type,
        @Nullable Class<?> tag,
        @Nullable Function<Graph, GraphCondition.ConditionResult> condition,
        List<Node<?>> createDependencies,
        List<Node<?>> refreshDependencies,
        Supplier<List<Node<?>>> promiseDependencies,
        List<Node<? extends GraphInterceptor<T>>> interceptors,
        Graph.Factory<? extends T> factory) {
        for (var dependency : createDependencies) {
            switch (dependency) {
                case NodeImpl<?> node -> {
                    if (node.index >= 0 && node.graphDraw != this) {
                        throw new IllegalArgumentException("Graph dependency belongs to another application graph: node index %s, type %s".formatted(node.index, node.type));
                    }
                }
            }
        }

        var node = new NodeImpl<>(
            this,
            type,
            tag,
            condition,
            this.graphNodes.size(),
            createDependencies,
            refreshDependencies,
            promiseDependencies,
            interceptors,
            factory
        );
        this.graphNodes.add(node);
        return node;
    }

    public InitializedGraph init() {
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
            if (graphNode.type().equals(type) && graphNode.tag() == null) {
                return graphNode;
            }
        }
        return null;
    }

    public List<Node<?>> findNodesByType(Type type, @Nullable Class<?> tag) {
        var result = new ArrayList<Node<?>>();
        for (var graphNode : this.graphNodes) {
            if (graphNode.type().equals(type)) {
                if (Objects.equals(tag, graphNode.tag()) || tag != null && tag.getCanonicalName().equals("io.koraframework.common.annotation.Tag.Any")) {
                    result.add(graphNode);
                }
            }
        }
        return result;
    }

    public <T> void replaceNode(Node<T> node, Graph.Factory<? extends T> factory) {
        var casted = (NodeImpl<T>) node;
        this.graphNodes.set(casted.index, new NodeImpl<T>(
            this, casted.type, casted.tag, casted.condition, casted.index, List.of(), List.of(), List::of, List.of(), factory
        ));
    }

    public <T> void replaceNodeKeepDependencies(Node<T> node, Graph.Factory<? extends T> factory) {
        var casted = (NodeImpl<T>) node;
        this.graphNodes.set(casted.index, new NodeImpl<T>(
            this, casted.type, casted.tag, casted.condition, casted.index, casted.createDependencies, casted.refreshDependencies, casted.promiseDependencies, List.of(), factory
        ));
    }

    @SuppressWarnings("unchecked")
    public ApplicationGraphDraw copy() {
        var draw = new ApplicationGraphDraw(this.root);
        var nodes = new ArrayList<>(this.graphNodes);
        var it = nodes.listIterator();
        class Helper {
            <T> void addNode(ApplicationGraphDraw draw, NodeImpl<T> node) {
                var createDependencies = new ArrayList<Node<?>>(node.createDependencies.size());
                for (var dependency : node.createDependencies) {
                    switch (dependency) {
                        case NodeImpl<?> v -> {
                            createDependencies.add(draw.graphNodes.get(v.index));
                        }
                    }
                }
                var refreshDependencies = new ArrayList<Node<?>>(node.refreshDependencies.size());
                for (var dependency : node.refreshDependencies) {
                    switch (dependency) {
                        case NodeImpl<?> v -> {
                            refreshDependencies.add(draw.graphNodes.get(v.index));
                        }
                    }
                }
                var interceptors = new ArrayList<Node<? extends GraphInterceptor<T>>>(node.interceptors.size());
                for (var interceptor : node.interceptors) {
                    interceptors.add((Node<? extends GraphInterceptor<T>>) draw.graphNodes.get(((NodeImpl<?>) interceptor).index));
                }

                var condition = node.condition();
                var replacedNode = draw.<T>addNode(
                    node.type(),
                    node.tag(),
                    condition == null ? null : graph -> condition.apply(ReplacedGraphFactory.replaced(nodes, graph)),
                    createDependencies,
                    refreshDependencies,
                    () -> node.promiseDependencies.get().stream().<Node<?>>map(v -> draw.graphNodes.get(((NodeImpl<?>) v).index)).toList(),
                    interceptors,
                    new ReplacedGraphFactory<>(nodes, node)
                );
                it.set((NodeImpl<?>) replacedNode);
            }
        }
        var helper = new Helper();
        while (it.hasNext()) {
            var node = it.next();
            helper.addNode(draw, node);
        }
        return draw;
    }

    @SuppressWarnings("unchecked")
    static class ReplacedGraphFactory<T> implements Graph.Factory<T> {
        final List<NodeImpl<?>> nodes;
        final NodeImpl<T> node;

        public ReplacedGraphFactory(List<NodeImpl<?>> nodes, NodeImpl<T> node) {
            this.nodes = nodes;
            this.node = node;
        }

        @Override
        public T get(RefreshableGraph graph) throws Exception {
            return node.factory.get(replaced(this.nodes, graph));
        }

        static RefreshableGraph replaced(List<NodeImpl<?>> nodes, Graph graph) {
            return new RefreshableGraph() {
                @Override
                public void refresh(Node<?> fromNode) {
                    ((RefreshableGraph) graph).refresh(switch (fromNode) {
                        case NodeImpl<?> n -> nodes.get(n.index);
                    });
                }

                @Override
                public ApplicationGraphDraw draw() {
                    return graph.draw();
                }

                @Override
                public <N> N get(Node<? extends N> node1) {
                    return switch (node1) {
                        case NodeImpl<? extends N> n -> graph.get((Node<N>) nodes.get(n.index));
                    };
                }

                @Override
                public <N> ValueOf<N> valueOf(Node<? extends N> node1) {
                    return switch (node1) {
                        case NodeImpl<? extends N> n -> graph.valueOf((Node<N>) nodes.get(n.index));
                    };
                }

                @Override
                public <N> PromiseOf<N> promiseOf(Node<? extends N> node1) {
                    return switch (node1) {
                        case NodeImpl<? extends N> n -> graph.promiseOf((Node<N>) nodes.get(n.index));
                    };
                }

                @Override
                public <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
                    NodeWithMapper<N, V>[] fixed = new NodeWithMapper[oneOfNodes.length];
                    for (int i = 0; i < oneOfNodes.length; i++) {
                        switch (oneOfNodes[i].node()) {
                            case NodeImpl<? extends N> n -> fixed[i] = new NodeWithMapper<>((Node<N>) nodes.get(n.index), oneOfNodes[i].mapper());
                        }
                    }

                    return graph.getOnePromiseOf(fixed);
                }
            };
        }
    }

    public ApplicationGraphDraw subgraph(List<Node<?>> excludeTransitive, Iterable<Node<?>> rootNodes) {
        var seen = new TreeMap<Integer, Integer>();
        var excludeTransitiveSet = excludeTransitive.stream().map(n -> ((NodeImpl<?>) n).index).collect(Collectors.toSet());

        var subgraph = new ApplicationGraphDraw(this.root);
        var pendingPromiseDependencies = new ArrayDeque<NodeImpl<?>>();
        var visitor = new Object() {
            public <T> Node<T> accept(NodeImpl<T> node) {
                if (!seen.containsKey(node.index)) {
                    var dependencyNodes = new ArrayList<Node<?>>();
                    var interceptors = new ArrayList<Node<? extends GraphInterceptor<T>>>();
                    if (!excludeTransitiveSet.contains(node.index)) {
                        for (var dependencyNode : node.createDependencies) {
                            switch (dependencyNode) {
                                case NodeImpl<?> v -> {
                                    var n = this.accept(v);
                                    dependencyNodes.add(n);
                                }
                            }
                        }
                    }
                    for (var interceptor : node.interceptors) {
                        interceptors.add(this.accept((NodeImpl<? extends GraphInterceptor<T>>) interceptor));
                    }
                    Graph.Factory<T> factory = graph -> node.factory.get(this.remapped(graph));
                    var condition = node.condition();
                    Supplier<List<Node<?>>> promiseDependencies = () -> node.promiseDependencies.get().stream()
                        .map(v -> seen.get(((NodeImpl<?>) v).index))
                        .filter(Objects::nonNull)
                        .<Node<?>>map(subgraph.graphNodes::get)
                        .toList();
                    var newNode = (NodeImpl<T>) subgraph.addNode(node.type(), node.tag(),
                        condition == null ? null : graph -> condition.apply(this.remapped(graph)),
                        dependencyNodes, dependencyNodes, promiseDependencies, interceptors, factory);// todo
                    seen.put(node.index, newNode.index);
                    if (!excludeTransitiveSet.contains(node.index)) {
                        // promised nodes are not initialization dependencies: visit them after the current dependency tree is added
                        for (var promiseDependency : node.promiseDependencies.get()) {
                            switch (promiseDependency) {
                                case NodeImpl<?> v -> pendingPromiseDependencies.add(v);
                            }
                        }
                    }
                    return newNode;
                }
                var index = seen.get(node.index);
                @SuppressWarnings("unchecked")
                var newNode = (Node<T>) subgraph.graphNodes.get(index);
                return newNode;
            }

            @SuppressWarnings("unchecked")
            private <Q> Node<Q> remappedNode(Node<? extends Q> node) {
                var index = seen.get(((NodeImpl<?>) node).index);
                if (index == null) {
                    throw new IllegalStateException("Node %s is not a part of the subgraph".formatted(node));
                }
                return (Node<Q>) subgraph.graphNodes.get(index);
            }

            private RefreshableGraph remapped(Graph graph) {
                return new RefreshableGraph() {
                    @Override
                    public void refresh(Node<?> fromNode) {
                        ((RefreshableGraph) graph).refresh(remappedNode(fromNode));
                    }

                    @Override
                    public ApplicationGraphDraw draw() {
                        return subgraph;
                    }


                    @Override
                    public <Q> Q get(Node<? extends Q> node1) {
                        return graph.get(remappedNode(node1));
                    }

                    @Override
                    public <Q> ValueOf<Q> valueOf(Node<? extends Q> node1) {
                        return graph.valueOf(remappedNode(node1));
                    }

                    @Override
                    public <Q> PromiseOf<Q> promiseOf(Node<? extends Q> node1) {
                        return graph.promiseOf(remappedNode(node1));
                    }


                    @Override
                    @SuppressWarnings("unchecked")
                    public <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
                        NodeWithMapper<N, V>[] fixed = new NodeWithMapper[oneOfNodes.length];
                        for (int i = 0; i < oneOfNodes.length; i++) {
                            fixed[i] = new NodeWithMapper<>(remappedNode(oneOfNodes[i].node()), oneOfNodes[i].mapper());
                        }

                        return graph.getOnePromiseOf(fixed);
                    }
                };
            }
        };
        for (var rootNode : rootNodes) {
            var casted = (NodeImpl<?>) rootNode;
            visitor.accept(this.graphNodes.get(casted.index));
        }
        while (!pendingPromiseDependencies.isEmpty()) {
            visitor.accept(pendingPromiseDependencies.poll());
        }
        return subgraph;
    }
}
