package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.*;
import io.koraframework.application.graph.internal.optimized.*;
import org.jspecify.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class ApplicationGraphDraw {

    private final Class<?> root;
    private final NodeImpl<?>[] graphNodes;
    private int size = 0;

    private final int[] createEdges;
    private int createEdgesSize = 0;
    private final int[] createOffsets;

    private final int[] refreshEdges;
    private int refreshEdgesSize = 0;
    private final int[] refreshOffsets;

    private final int[] downstreamEdges;
    private int downstreamEdgesSize = 0;
    private final int[] downstreamOffsets;

    private final int[] interceptorEdges;
    private int interceptorEdgesSize = 0;
    private final int[] interceptorOffsets;
    private final Type[] nodeTypes;
    private final @Nullable Class<?>[] nodeTags;
    private final @Nullable Function<Graph, GraphCondition.ConditionResult>[] nodeConditions;
    private final Graph.Factory<?>[] nodeFactories;
    private final TypeIntMap typeFirstIndex;
    private final int[] nextTypeNode;
    private final boolean[] hasCustomRefresh;

    public ApplicationGraphDraw(Class<?> root, int expectedNodes, int expectedCreateEdges, int expectedRefreshEdges, int expectedInterceptorEdges, int expectedDownstreamEdges) {
        this.root = root;
        createOffsets = new int[expectedNodes + 1];
        refreshOffsets = new int[expectedNodes + 1];
        interceptorOffsets = new int[expectedNodes + 1];
        downstreamOffsets = new int[expectedNodes + 1];

        createEdges = new int[expectedCreateEdges];
        refreshEdges = new int[expectedRefreshEdges];
        interceptorEdges = new int[expectedInterceptorEdges];
        downstreamEdges = new int[expectedDownstreamEdges];

        typeFirstIndex = new TypeIntMap(expectedNodes);
        nextTypeNode = new int[expectedNodes];
        Arrays.fill(nextTypeNode, -1);
        nodeTypes = new Type[expectedNodes];
        nodeTags = new Class<?>[expectedNodes];
        nodeConditions = new Function[expectedNodes];
        nodeFactories = new Graph.Factory<?>[expectedNodes];
        hasCustomRefresh = new boolean[expectedNodes];

        graphNodes = new NodeImpl<?>[expectedNodes];
        for (int i = 0; i < expectedNodes; i++) {
            graphNodes[i] = new NodeImpl<>(this, i);
        }
    }

    public Class<?> getRoot() {
        return root;
    }

    public NodeImpl<?> getNodeByIndex(int index) {
        return graphNodes[index];
    }

    @SuppressWarnings("unchecked")
    public <T> Graph.Factory<T> getNodeFactory(int index) {
        return (Graph.Factory<T>) nodeFactories[index];
    }

    public int size() {
        return size;
    }

    public int getCreateDependenciesCount(int nodeIndex) {
        return createOffsets[nodeIndex + 1] - createOffsets[nodeIndex];
    }

    public boolean hasCustomRefresh(int index) {
        return hasCustomRefresh[index];
    }

    public <T> Node<T> addNode(
        Type type,
        @Nullable Class<?> tag,
        @Nullable Function<Graph, GraphCondition.ConditionResult> condition,
        int @Nullable [] createDependencies,
        int @Nullable [] refreshDependencies,
        int @Nullable [] downstreamConsumers,
        NodeImpl<?> @Nullable [] interceptors,
        Graph.Factory<?> factory
    ) {
        var currentIndex = size;

        createOffsets[currentIndex] = createEdgesSize;
        if (createDependencies != null && createDependencies.length > 0) {
            System.arraycopy(createDependencies, 0, createEdges, createEdgesSize, createDependencies.length);
            createEdgesSize += createDependencies.length;
        }
        createOffsets[currentIndex + 1] = createEdgesSize;

        refreshOffsets[currentIndex] = refreshEdgesSize;
        if (refreshDependencies != null) {
            hasCustomRefresh[currentIndex] = true;
            if (refreshDependencies.length > 0) {
                System.arraycopy(refreshDependencies, 0, refreshEdges, refreshEdgesSize, refreshDependencies.length);
                refreshEdgesSize += refreshDependencies.length;
            }
        }
        refreshOffsets[currentIndex + 1] = refreshEdgesSize;

        interceptorOffsets[currentIndex] = interceptorEdgesSize;
        if (interceptors != null && interceptors.length > 0) {
            for (int i = 0; i < interceptors.length; i++) {
                interceptorEdges[interceptorEdgesSize++] = interceptors[i].index;
            }
        }
        interceptorOffsets[currentIndex + 1] = interceptorEdgesSize;

        downstreamOffsets[currentIndex] = downstreamEdgesSize;
        if (downstreamConsumers != null && downstreamConsumers.length > 0) {
            System.arraycopy(downstreamConsumers, 0, downstreamEdges, downstreamEdgesSize, downstreamConsumers.length);
            downstreamEdgesSize += downstreamConsumers.length;
        }
        downstreamOffsets[currentIndex + 1] = downstreamEdgesSize;

        nodeTypes[currentIndex] = type;
        nodeTags[currentIndex] = tag;
        nodeConditions[currentIndex] = condition;
        nodeFactories[currentIndex] = factory;
        size++;

        var firstIdx = typeFirstIndex.get(type);
        if (firstIdx != -1) {
            nextTypeNode[currentIndex] = firstIdx;
        }
        typeFirstIndex.put(type, currentIndex);

        return (Node<T>) graphNodes[currentIndex];
    }

    public InitializedGraph init() {
        var graph = new OptimizedGraphImpl(this);
        graph.init();
        return graph;
    }

    public List<Node<?>> getNodes() {
        return new GraphNodeList(graphNodes, size);
    }

    @Nullable
    public Node<?> findNodeByType(Type type) {
        var current = typeFirstIndex.get(type);
        while (current != -1) {
            var graphNode = graphNodes[current];
            if (graphNode.tag() == null) {
                return graphNode;
            }
            current = nextTypeNode[current];
        }
        return null;
    }

    public List<Node<?>> findNodesByType(Type type, @Nullable Class<?> tag) {
        var current = typeFirstIndex.get(type);
        if (current == -1) {
            return List.of();
        }

        var result = new ArrayList<Node<?>>();
        boolean isAnyTag = tag != null && "io.koraframework.common.Tag.Any".equals(tag.getName());

        while (current != -1) {
            var graphNode = graphNodes[current];
            if (isAnyTag || Objects.equals(tag, graphNode.tag())) {
                result.add(graphNode);
            }
            current = nextTypeNode[current];
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> void replaceNode(Node<T> node, Graph.Factory<? extends T> factory) {
        var casted = (NodeImpl<T>) node;
        var idx = casted.index;

        int createRemoved = createOffsets[idx + 1] - createOffsets[idx];
        if (createRemoved > 0) {
            int oldEnd = createOffsets[idx + 1];
            System.arraycopy(createEdges, oldEnd, createEdges, createOffsets[idx], createEdgesSize - oldEnd);
            createEdgesSize -= createRemoved;
            for (int i = idx + 1; i <= size; i++) {
                createOffsets[i] -= createRemoved;
            }
        }

        int refreshRemoved = refreshOffsets[idx + 1] - refreshOffsets[idx];
        if (refreshRemoved > 0) {
            int oldEnd = refreshOffsets[idx + 1];
            System.arraycopy(refreshEdges, oldEnd, refreshEdges, refreshOffsets[idx], refreshEdgesSize - oldEnd);
            refreshEdgesSize -= refreshRemoved;
            for (int i = idx + 1; i <= size; i++) {
                refreshOffsets[i] -= refreshRemoved;
            }
        }

        int interRemoved = interceptorOffsets[idx + 1] - interceptorOffsets[idx];
        if (interRemoved > 0) {
            int oldEnd = interceptorOffsets[idx + 1];
            System.arraycopy(interceptorEdges, oldEnd, interceptorEdges, interceptorOffsets[idx], interceptorEdgesSize - oldEnd);
            interceptorEdgesSize -= interRemoved;
            for (int i = idx + 1; i <= size; i++) {
                interceptorOffsets[i] -= interRemoved;
            }
        }

        int writePos = 0;
        for (int n = 0; n < size; n++) {
            int start = downstreamOffsets[n];
            int end = downstreamOffsets[n + 1];
            downstreamOffsets[n] = writePos;
            for (int j = start; j < end; j++) {
                if (downstreamEdges[j] != idx) {
                    downstreamEdges[writePos++] = downstreamEdges[j];
                }
            }
        }
        downstreamOffsets[size] = writePos;
        downstreamEdgesSize = writePos;

        nodeFactories[idx] = factory;

        var dsStart = downstreamOffsets[idx];
        var dsEnd = downstreamOffsets[idx + 1];
        for (int k = dsStart; k < dsEnd; k++) {
            int consumerIdx = downstreamEdges[k];
            if (consumerIdx != idx && hasRefreshDependency(consumerIdx, idx) && !(nodeFactories[consumerIdx] instanceof ReplacedSingleFactory<?>)) {
                nodeFactories[consumerIdx] = new ReplacedSingleFactory<>((Graph.Factory<Object>) nodeFactories[consumerIdx], graphNodes);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void replaceNodeKeepDependencies(Node<T> node, Graph.Factory<? extends T> factory) {
        var casted = (NodeImpl<T>) node;
        var idx = casted.index;
        nodeFactories[idx] = factory;

        var dsStart = downstreamOffsets[idx];
        var dsEnd = downstreamOffsets[idx + 1];
        for (int k = dsStart; k < dsEnd; k++) {
            int consumerIdx = downstreamEdges[k];
            if (consumerIdx != idx && hasRefreshDependency(consumerIdx, idx) && !(nodeFactories[consumerIdx] instanceof ReplacedSingleFactory<?>)) {
                nodeFactories[consumerIdx] = new ReplacedSingleFactory<>((Graph.Factory<Object>) nodeFactories[consumerIdx], graphNodes);
            }
        }
    }

    private boolean hasRefreshDependency(int consumerIdx, int providerIdx) {
        if (!hasCustomRefresh[consumerIdx]) {
            int start = createOffsets[consumerIdx];
            int end = createOffsets[consumerIdx + 1];
            for (int i = start; i < end; i++) {
                if (createEdges[i] == providerIdx) {
                    return true;
                }
            }
            return false;
        }
        int start = refreshOffsets[consumerIdx];
        int end = refreshOffsets[consumerIdx + 1];
        for (int i = start; i < end; i++) {
            if (refreshEdges[i] == providerIdx) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public ApplicationGraphDraw copy() {
        int totalDownstream = createEdgesSize + interceptorEdgesSize;
        var draw = new ApplicationGraphDraw(root, size, createEdgesSize, refreshEdgesSize, interceptorEdgesSize, totalDownstream);

        System.arraycopy(createOffsets, 0, draw.createOffsets, 0, size + 1);
        System.arraycopy(refreshOffsets, 0, draw.refreshOffsets, 0, size + 1);
        System.arraycopy(interceptorOffsets, 0, draw.interceptorOffsets, 0, size + 1);

        System.arraycopy(createEdges, 0, draw.createEdges, 0, createEdgesSize);
        System.arraycopy(refreshEdges, 0, draw.refreshEdges, 0, refreshEdgesSize);
        System.arraycopy(interceptorEdges, 0, draw.interceptorEdges, 0, interceptorEdgesSize);

        System.arraycopy(nodeTypes, 0, draw.nodeTypes, 0, size);
        System.arraycopy(nodeTags, 0, draw.nodeTags, 0, size);
        System.arraycopy(nodeConditions, 0, draw.nodeConditions, 0, size);
        System.arraycopy(hasCustomRefresh, 0, draw.hasCustomRefresh, 0, size);

        draw.size = size;
        draw.createEdgesSize = createEdgesSize;
        draw.refreshEdgesSize = refreshEdgesSize;
        draw.interceptorEdgesSize = interceptorEdgesSize;
        draw.downstreamEdgesSize = totalDownstream;

        var downstreamCounts = new int[size];
        for (int i = 0; i < createEdgesSize; i++) {
            downstreamCounts[createEdges[i]]++;
        }
        for (int i = 0; i < interceptorEdgesSize; i++) {
            downstreamCounts[interceptorEdges[i]]++;
        }

        int currentOffset = 0;
        for (int i = 0; i < size; i++) {
            draw.downstreamOffsets[i] = currentOffset;
            currentOffset += downstreamCounts[i];
        }
        draw.downstreamOffsets[size] = currentOffset;

        System.arraycopy(draw.downstreamOffsets, 0, downstreamCounts, 0, size);

        for (int consumerIdx = 0; consumerIdx < size; consumerIdx++) {
            int start = createOffsets[consumerIdx];
            int end = createOffsets[consumerIdx + 1];
            for (int j = start; j < end; j++) {
                int providerIdx = createEdges[j];
                draw.downstreamEdges[downstreamCounts[providerIdx]++] = consumerIdx;
            }
        }
        for (int consumerIdx = 0; consumerIdx < size; consumerIdx++) {
            int start = interceptorOffsets[consumerIdx];
            int end = interceptorOffsets[consumerIdx + 1];
            for (int j = start; j < end; j++) {
                int providerIdx = interceptorEdges[j];
                draw.downstreamEdges[downstreamCounts[providerIdx]++] = consumerIdx;
            }
        }

        for (int i = 0; i < size; i++) {
            var condition = nodeConditions[i];
            if (condition != null) {
                draw.nodeConditions[i] = new ReplacedCondition(condition, draw.graphNodes);
            }

            draw.nodeFactories[i] = nodeFactories[i];
            var firstIdx = draw.typeFirstIndex.get(nodeTypes[i]);
            if (firstIdx != -1) {
                draw.nextTypeNode[i] = firstIdx;
            }
            draw.typeFirstIndex.put(nodeTypes[i], i);
        }
        return draw;
    }

    public ApplicationGraphDraw subgraph(List<Node<?>> excludeTransitive, Iterable<Node<?>> rootNodes) {
        var maxNodes = size;

        var seen = new int[maxNodes];
        Arrays.fill(seen, -1);

        var stack = new int[maxNodes];
        var inStack = new boolean[maxNodes];
        int stackPtr = 0;
        var state = new byte[maxNodes];

        for (var i = 0; i < excludeTransitive.size(); i++) {
            state[((NodeImpl<?>) excludeTransitive.get(i)).index] = 3;
        }

        for (var rootNode : rootNodes) {
            var rootIdx = ((NodeImpl<?>) rootNode).index;
            if (seen[rootIdx] == -1 && !inStack[rootIdx] && (state[rootIdx] == 0 || state[rootIdx] == 3)) {
                stack[stackPtr++] = rootIdx;
                inStack[rootIdx] = true;
            }
        }

        int subNodesCount = 0;
        int subCreateEdgesCount = 0;
        int subRefreshEdgesCount = 0;
        int subInterceptorEdgesCount = 0;

        var targetNodes = new int[maxNodes];

        while (stackPtr > 0) {
            var currentIdx = stack[stackPtr - 1];
            var currentState = state[currentIdx];

            if (currentState == 0 || currentState == 3) {
                state[currentIdx] = 1;

                var interStart = interceptorOffsets[currentIdx];
                var interEnd = interceptorOffsets[currentIdx + 1];
                for (var i = interEnd - 1; i >= interStart; i--) {
                    int interIdx = interceptorEdges[i];
                    if (seen[interIdx] == -1 && !inStack[interIdx] && (state[interIdx] == 0 || state[interIdx] == 3)) {
                        stack[stackPtr++] = interIdx;
                        inStack[interIdx] = true;
                    }
                }

                if (currentState == 0) {
                    var createStart = createOffsets[currentIdx];
                    var createEnd = createOffsets[currentIdx + 1];
                    for (var i = createEnd - 1; i >= createStart; i--) {
                        var depIdx = createEdges[i];
                        if (seen[depIdx] == -1 && !inStack[depIdx] && (state[depIdx] == 0 || state[depIdx] == 3)) {
                            stack[stackPtr++] = depIdx;
                            inStack[depIdx] = true;
                        }
                    }
                } else {
                    state[currentIdx] = -1;
                }
            } else if (currentState == 1 || currentState == -1) {
                stackPtr--;
                inStack[currentIdx] = false;
                state[currentIdx] = (byte) (currentState == 1 ? 2 : 4);

                if (seen[currentIdx] == -1) {
                    seen[currentIdx] = subNodesCount;
                    targetNodes[subNodesCount++] = currentIdx;

                    if (state[currentIdx] == 2) {
                        subCreateEdgesCount += createOffsets[currentIdx + 1] - createOffsets[currentIdx];
                        subRefreshEdgesCount += refreshOffsets[currentIdx + 1] - refreshOffsets[currentIdx];
                    }
                    subInterceptorEdgesCount += interceptorOffsets[currentIdx + 1] - interceptorOffsets[currentIdx];
                }
            } else {
                stackPtr--;
                inStack[currentIdx] = false;
            }
        }

        var subDownstreamEdgesCount = subCreateEdgesCount + subInterceptorEdgesCount;

        var subgraph = new ApplicationGraphDraw(root, subNodesCount, subCreateEdgesCount, subRefreshEdgesCount, subInterceptorEdgesCount, subDownstreamEdgesCount);
        subgraph.size = subNodesCount;

        var subDownstreamCounts = new int[subNodesCount];

        for (int i = 0; i < subNodesCount; i++) {
            var oldIdx = targetNodes[i];

            subgraph.createOffsets[i] = subgraph.createEdgesSize;
            if (state[oldIdx] == 2) {
                var start = createOffsets[oldIdx];
                var end = createOffsets[oldIdx + 1];
                for (var j = start; j < end; j++) {
                    var providerSubIdx = seen[createEdges[j]];
                    subgraph.createEdges[subgraph.createEdgesSize++] = providerSubIdx;
                    subDownstreamCounts[providerSubIdx]++;
                }
            }
            subgraph.createOffsets[i + 1] = subgraph.createEdgesSize;

            subgraph.refreshOffsets[i] = subgraph.refreshEdgesSize;
            if (state[oldIdx] == 2 && hasCustomRefresh[oldIdx]) {
                subgraph.hasCustomRefresh[i] = true;
                int start = refreshOffsets[oldIdx];
                int end = refreshOffsets[oldIdx + 1];
                for (int j = start; j < end; j++) {
                    subgraph.refreshEdges[subgraph.refreshEdgesSize++] = seen[refreshEdges[j]];
                }
            }
            subgraph.refreshOffsets[i + 1] = subgraph.refreshEdgesSize;

            subgraph.interceptorOffsets[i] = subgraph.interceptorEdgesSize;
            var instart = interceptorOffsets[oldIdx];
            var inend = interceptorOffsets[oldIdx + 1];
            for (var j = instart; j < inend; j++) {
                var interIdx = seen[interceptorEdges[j]];
                subgraph.interceptorEdges[subgraph.interceptorEdgesSize++] = interIdx;
                subDownstreamCounts[interIdx]++;
            }
            subgraph.interceptorOffsets[i + 1] = subgraph.interceptorEdgesSize;

            subgraph.nodeTypes[i] = nodeTypes[oldIdx];
            subgraph.nodeTags[i] = nodeTags[oldIdx];

            var condition = nodeConditions[oldIdx];
            subgraph.nodeConditions[i] = condition == null ? null : new SharedSeenCondition(condition, seen);
            subgraph.nodeFactories[i] = new SharedSeenFactory<>(nodeFactories[oldIdx], seen);

            var firstIdx = subgraph.typeFirstIndex.get(subgraph.nodeTypes[i]);
            if (firstIdx != -1) {
                subgraph.nextTypeNode[i] = firstIdx;
            }
            subgraph.typeFirstIndex.put(subgraph.nodeTypes[i], i);
        }

        int currentDownstreamOffset = 0;
        for (int i = 0; i < subNodesCount; i++) {
            subgraph.downstreamOffsets[i] = currentDownstreamOffset;
            currentDownstreamOffset += subDownstreamCounts[i];
        }
        subgraph.downstreamOffsets[subNodesCount] = currentDownstreamOffset;

        System.arraycopy(subgraph.downstreamOffsets, 0, subDownstreamCounts, 0, subNodesCount);

        for (int consumerSubIdx = 0; consumerSubIdx < subNodesCount; consumerSubIdx++) {
            var start = subgraph.createOffsets[consumerSubIdx];
            var end = subgraph.createOffsets[consumerSubIdx + 1];
            for (var j = start; j < end; j++) {
                var providerSubIdx = subgraph.createEdges[j];
                var writePos = subDownstreamCounts[providerSubIdx]++;
                subgraph.downstreamEdges[writePos] = consumerSubIdx;
            }
        }

        for (int consumerSubIdx = 0; consumerSubIdx < subNodesCount; consumerSubIdx++) {
            var start = subgraph.interceptorOffsets[consumerSubIdx];
            var end = subgraph.interceptorOffsets[consumerSubIdx + 1];
            for (var j = start; j < end; j++) {
                var providerSubIdx = subgraph.interceptorEdges[j];
                var writePos = subDownstreamCounts[providerSubIdx]++;
                subgraph.downstreamEdges[writePos] = consumerSubIdx;
            }
        }

        subgraph.downstreamEdgesSize = subDownstreamEdgesCount;

        return subgraph;
    }

    public int[] getCreateEdges() {
        return createEdges;
    }

    public int[] getCreateOffsets() {
        return createOffsets;
    }

    public int[] getRefreshEdges() {
        return refreshEdges;
    }

    public int[] getRefreshOffsets() {
        return refreshOffsets;
    }

    public int[] getInterceptorEdges() {
        return interceptorEdges;
    }

    public int[] getInterceptorOffsets() {
        return interceptorOffsets;
    }

    public int[] getDownstreamEdges() {
        return downstreamEdges;
    }

    public int[] getDownstreamOffsets() {
        return downstreamOffsets;
    }

    public Type getNodeType(int index) {
        return nodeTypes[index];
    }

    @Nullable
    public Class<?> getNodeTag(int index) {
        return nodeTags[index];
    }

    @Nullable
    public Function<Graph, GraphCondition.ConditionResult> getNodeCondition(int index) {
        return nodeConditions[index];
    }

    public List<Node<?>> getNodeInterceptors(int nodeIndex) {
        int start = interceptorOffsets[nodeIndex];
        int end = interceptorOffsets[nodeIndex + 1];
        if (start == end) {
            return List.of();
        }
        return new CsrNodeList(graphNodes, interceptorEdges, start, end);
    }

    public List<Node<?>> getNodeCreateDependencies(int nodeIndex) {
        int start = createOffsets[nodeIndex];
        int end = createOffsets[nodeIndex + 1];
        if (start == end) {
            return List.of();
        }
        return new CsrNodeList(graphNodes, createEdges, start, end);
    }

    public List<Node<?>> getNodeRefreshDependencies(int nodeIndex) {
        int start = refreshOffsets[nodeIndex];
        int end = refreshOffsets[nodeIndex + 1];
        if (start == end) {
            return List.of();
        }
        return new CsrNodeList(graphNodes, refreshEdges, start, end);
    }

    private static final class ReplacedGraphProxy implements RefreshableGraph {
        private final Graph graph;
        private final NodeImpl<?>[] nodes;

        ReplacedGraphProxy(Graph graph, NodeImpl<?>[] nodes) {
            this.graph = graph;
            this.nodes = nodes;
        }

        @Override
        public void refresh(Node<?> fromNode) {
            var n = (NodeImpl<?>) fromNode;
            ((RefreshableGraph) graph).refresh(nodes[n.index]);
        }

        @Override
        public ApplicationGraphDraw draw() {
            return graph.draw();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> N get(Node<? extends N> node1) {
            var n = (NodeImpl<?>) node1;
            return graph.get((Node<N>) nodes[n.index]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> ValueOf<N> valueOf(Node<? extends N> node1) {
            var n = (NodeImpl<?>) node1;
            return graph.valueOf((Node<N>) nodes[n.index]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> PromiseOf<N> promiseOf(Node<? extends N> node1) {
            var n = (NodeImpl<?>) node1;
            return graph.promiseOf((Node<N>) nodes[n.index]);
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("unchecked")
        public final <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
            var fixed = new NodeWithMapper[oneOfNodes.length];
            for (var i = 0; i < oneOfNodes.length; i++) {
                var n = (NodeImpl<?>) oneOfNodes[i].node();
                fixed[i] = new NodeWithMapper<>((Node<N>) nodes[n.index], oneOfNodes[i].mapper());
            }
            return graph.getOnePromiseOf(fixed);
        }
    }

    static final class ReplacedCondition implements Function<Graph, GraphCondition.ConditionResult> {
        private final Function<Graph, GraphCondition.ConditionResult> delegate;
        private final NodeImpl<?>[] targetNodes;

        ReplacedCondition(Function<Graph, GraphCondition.ConditionResult> delegate, NodeImpl<?>[] targetNodes) {
            this.delegate = delegate;
            this.targetNodes = targetNodes;
        }

        @Override
        public GraphCondition.ConditionResult apply(Graph graph) {
            return delegate.apply(new ReplacedGraphProxy(graph, targetNodes));
        }
    }

    static final class ReplacedSingleFactory<T> implements Graph.Factory<T> {
        private final Graph.Factory<T> delegate;
        private final NodeImpl<?>[] targetNodes;

        ReplacedSingleFactory(Graph.Factory<T> delegate, NodeImpl<?>[] targetNodes) {
            this.delegate = delegate;
            this.targetNodes = targetNodes;
        }

        @Override
        public T get(RefreshableGraph graph) throws Exception {
            return delegate.get(new ReplacedGraphProxy(graph, targetNodes));
        }

        @Override
        public T get(RefreshableGraph graph, int index) throws Exception {
            return delegate.get(new ReplacedGraphProxy(graph, targetNodes), index);
        }
    }

    static final class SharedSeenCondition implements Function<Graph, GraphCondition.ConditionResult> {
        private final Function<Graph, GraphCondition.ConditionResult> delegate;
        private final int[] seen;

        SharedSeenCondition(Function<Graph, GraphCondition.ConditionResult> delegate, int[] seen) {
            this.delegate = delegate;
            this.seen = seen;
        }

        @Override
        public GraphCondition.ConditionResult apply(Graph graph) {
            return delegate.apply(new SubgraphGraphProxy(graph, seen));
        }
    }

    static final class SharedSeenFactory<T> implements Graph.Factory<T> {
        private final Graph.Factory<T> delegate;
        private final int[] seen;

        SharedSeenFactory(Graph.Factory<T> delegate, int[] seen) {
            this.delegate = delegate;
            this.seen = seen;
        }

        @Override
        public T get(RefreshableGraph graph) throws Exception {
            return delegate.get(new SubgraphGraphProxy(graph, seen));
        }
    }

    static final class SubgraphGraphProxy implements RefreshableGraph {
        private final Graph delegate;
        private final int[] seen;

        SubgraphGraphProxy(Graph delegate, int[] seen) {
            this.delegate = delegate;
            this.seen = seen;
        }

        @Override
        public void refresh(Node<?> fromNode) {
            var n = (NodeImpl<?>) fromNode;
            ((RefreshableGraph) delegate).refresh(delegate.draw().getNodeByIndex(seen[n.index]));
        }

        @Override
        public ApplicationGraphDraw draw() {
            return delegate.draw();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> N get(Node<? extends N> node) {
            var n = (NodeImpl<?>) node;
            return delegate.get((Node<N>) delegate.draw().getNodeByIndex(seen[n.index]));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> ValueOf<N> valueOf(Node<? extends N> node) {
            var n = (NodeImpl<?>) node;
            return delegate.valueOf((Node<N>) delegate.draw().getNodeByIndex(seen[n.index]));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <N> PromiseOf<N> promiseOf(Node<? extends N> node) {
            var n = (NodeImpl<?>) node;
            return delegate.promiseOf((Node<N>) delegate.draw().getNodeByIndex(seen[n.index]));
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("unchecked")
        public final <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
            var fixed = new NodeWithMapper[oneOfNodes.length];
            for (var i = 0; i < oneOfNodes.length; i++) {
                var n = (NodeImpl<?>) oneOfNodes[i].node();
                fixed[i] = new NodeWithMapper<>((Node<N>) delegate.draw().getNodeByIndex(seen[n.index]), oneOfNodes[i].mapper());
            }
            return delegate.getOnePromiseOf(fixed);
        }
    }
}
