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
    default <T> T getOneOf(Node<? extends T>... nodes) {
        var node = getOneNodeMatchingCondition(this, nodes);
        return this.get(node);
    }

    @SuppressWarnings("unchecked")
    default <T> ValueOf<T> getOneValueOf(Node<? extends T>... nodes) {
        return () -> {
            var node = getOneNodeMatchingCondition(this, nodes);
            return this.get(node);
        };
    }

    @SuppressWarnings("unchecked")
    <T> PromiseOf<T> getOnePromiseOf(Node<? extends T>... nodes);

    default GraphCondition condition(Node<? extends GraphCondition> node) {
        return get(node);
    }

    interface Factory<T> {
        T get(RefreshableGraph graph) throws Exception;
    }

    @SafeVarargs
    private static <T> Node<? extends T> getOneNodeMatchingCondition(Graph graph, Node<? extends T>... nodes) {
        var lastValue = (@Nullable Node<? extends T>) null;
        var errors = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Failed>>(nodes.length);
        var matchedNodes = new ArrayList<Node<? extends T>>(nodes.length);
        var matchReasons = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Matched>>(nodes.length);
        for (var node : nodes) {
            var condition = node.condition();
            if (condition == null) {
                lastValue = node;
                matchedNodes.add(node);
                matchReasons.add(Map.entry(node, new GraphCondition.ConditionResult.Matched("Node %s has no conditions".formatted(node.toString()))));
            } else {
                switch (condition.apply(graph)) {
                    case GraphCondition.ConditionResult.Failed failed -> errors.add(Map.entry(node, failed));
                    case GraphCondition.ConditionResult.Matched matched -> {
                        lastValue = node;
                        matchedNodes.add(node);
                        matchReasons.add(Map.entry(node, matched));
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
