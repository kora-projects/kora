package io.koraframework.application.graph;

import io.koraframework.application.graph.exception.MoreThanOneConditionalNodeMatches;
import io.koraframework.application.graph.exception.NoneOfConditionalNodeMatches;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

public interface Graph {
    ApplicationGraphDraw draw();

    <T> T get(Node<? extends T> node);

    /**
     * <b>Русский</b>: Возвращает значение узла либо {@code null}, если узел условный и его условие не выполнено.
     * Используется для {@code @Nullable} зависимостей на {@link io.koraframework.common.annotation.Conditional} компоненты.
     * <hr>
     * <b>English</b>: Returns the node value, or {@code null} when the node is conditional and its condition failed.
     * Used for {@code @Nullable} dependencies on {@link io.koraframework.common.annotation.Conditional} components.
     */
    default <T> @Nullable T getNullable(Node<? extends T> node) {
        if (this.conditionResult(node) instanceof GraphCondition.ConditionResult.Failed) {
            return null;
        }
        return this.get(node);
    }

    /**
     * <b>Русский</b>: Вычисляет условие узла на этом графе либо возвращает {@code null}, если узел безусловный.
     * Граф, который перемаппивает узлы (сабграф, копия), сначала находит свой узел: у него может быть другое условие,
     * например у мока условия нет.
     * <hr>
     * <b>English</b>: Evaluates the node condition against this graph, or returns {@code null} when the node is unconditional.
     * A graph that remaps nodes (subgraph, copy) resolves its own node first: it may have another condition,
     * e.g. a mock has none.
     */
    default GraphCondition.@Nullable ConditionResult conditionResult(Node<?> node) {
        var condition = node.condition();
        return condition == null ? null : condition.apply(this);
    }

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
            var conditionResult = graph.conditionResult(node.node());
            if (conditionResult == null) {
                lastValue = node;
                matchedNodes.add(node);
                matchReasons.add(Map.entry(node.node(), new GraphCondition.ConditionResult.Matched("Node %s has no conditions".formatted(node.toString()))));
            } else {
                switch (conditionResult) {
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
