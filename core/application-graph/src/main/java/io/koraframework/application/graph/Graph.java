package io.koraframework.application.graph;

import io.koraframework.application.graph.exception.MoreThanOneConditionalNodeMatches;
import io.koraframework.application.graph.exception.NoneOfConditionalNodeMatches;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

public interface Graph {
    ApplicationGraphDraw draw();

    <T> T get(Node<? extends T> node);

    <T> ValueOf<T> valueOf(Node<? extends T> node);

    <T> PromiseOf<T> promiseOf(Node<? extends T> node);

    @SuppressWarnings("unchecked")
    default <N, V> V getOneOf(NodeWithMapper<N, V>... nodes) {
        var node = getOneNodeMatchingCondition(this, nodes);
        var value = this.get(node.node());
        return node.mapper().apply(value);
    }

    @SuppressWarnings("unchecked")
    default <N, V> ValueOf<V> getOneValueOf(NodeWithMapper<N, V>... nodes) {
        return () -> {
            var node = getOneNodeMatchingCondition(this, nodes);
            var value = this.get(node.node());
            return node.mapper().apply(value);
        };
    }

    @SuppressWarnings("unchecked")
    <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes);

    default GraphCondition condition(Node<? extends GraphCondition> node) {
        return get(node);
    }

    interface Factory<T> {
        T get(RefreshableGraph graph) throws Exception;
    }

    @SafeVarargs
    private static <T, V> NodeWithMapper<T, V> getOneNodeMatchingCondition(Graph graph, NodeWithMapper<T, V>... nodes) {
        var lastValue = (@Nullable NodeWithMapper<T, V>) null;
        var errors = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Failed>>(nodes.length);
        var matchedNodes = new ArrayList<NodeWithMapper<? extends T, V>>(nodes.length);
        var matchReasons = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Matched>>(nodes.length);
        for (var node : nodes) {
            var condition = node.node().condition();
            if (condition == null) {
                lastValue = node;
                matchedNodes.add(node);
                matchReasons.add(Map.entry(node.node(), new GraphCondition.ConditionResult.Matched("Node %s has no conditions".formatted(node.toString()))));
            } else {
                switch (condition.apply(graph)) {
                    case GraphCondition.ConditionResult.Failed failed -> errors.add(Map.entry(node.node(), failed));
                    case GraphCondition.ConditionResult.Matched matched -> {
                        lastValue = node;
                        matchedNodes.add(node);
                        matchReasons.add(Map.entry(node.node(), matched));
                    }
                }
            }
        }
        if (matchedNodes.size() == 1) {
            assert lastValue != null;
            return lastValue;
        }
        if (matchedNodes.isEmpty()) {
            throw new NoneOfConditionalNodeMatches(errors);
        } else {
            throw new MoreThanOneConditionalNodeMatches(matchReasons);
        }
    }
}
