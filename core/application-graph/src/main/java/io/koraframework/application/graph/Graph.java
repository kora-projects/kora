package io.koraframework.application.graph;

import io.koraframework.application.graph.exception.*;
import org.jspecify.annotations.*;

import java.util.*;

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
        var condition = node.condition();
        if (condition != null && condition.apply(this) instanceof GraphCondition.ConditionResult.Failed) {
            return null;
        }
        return get(node);
    }

    <T> ValueOf<T> valueOf(Node<? extends T> node);

    <T> PromiseOf<T> promiseOf(Node<? extends T> node);

    @SuppressWarnings("unchecked")
    default <N, V> V getOneOf(NodeWithMapper<N, V>... nodes) {
        var node = getOneNodeMatchingCondition(this, nodes);
        var value = get(node.node());
        return node.mapper().apply(value);
    }

    @SuppressWarnings("unchecked")
    default <N, V> ValueOf<V> getOneValueOf(NodeWithMapper<N, V>... nodes) {
        return new OneValueOf<>(this, nodes);
    }

    @SuppressWarnings("unchecked")
    <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes);

    default GraphCondition condition(Node<? extends GraphCondition> node) {
        return get(node);
    }

    interface Factory<T> {
        T get(RefreshableGraph graph) throws Exception;

        default T get(RefreshableGraph graph, int index) throws Exception {
            return get(graph);
        }
    }

    final class OneValueOf<N, V> implements ValueOf<V> {
        private final Graph graph;
        private final NodeWithMapper<N, V>[] nodes;

        public OneValueOf(Graph graph, NodeWithMapper<N, V>[] nodes) {
            this.graph = graph;
            this.nodes = nodes;
        }

        @Override
        public V get() {
            var node = getOneNodeMatchingCondition(graph, nodes);
            var value = graph.get(node.node());
            return node.mapper().apply(value);
        }
    }

    @SafeVarargs
    private static <T, V> NodeWithMapper<T, V> getOneNodeMatchingCondition(Graph graph, NodeWithMapper<T, V>... nodes) {
        var matched = (@Nullable NodeWithMapper<T, V>) null;
        var matchCount = 0;

        for (int i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var condition = node.node().condition();
            if (condition == null) {
                matched = node;
                matchCount++;
            } else {
                var res = condition.apply(graph);
                if (res instanceof GraphCondition.ConditionResult.Matched) {
                    matched = node;
                    matchCount++;
                }
            }
        }

        if (matchCount == 1) {
            return matched;
        }

        return handleConditionalErrors(graph, nodes, matchCount);
    }

    private static <T, V> NodeWithMapper<T, V> handleConditionalErrors(Graph graph, NodeWithMapper<T, V>[] nodes, int matchCount) {
        if (matchCount == 0) {
            var errors = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Failed>>(nodes.length);
            for (int i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                var condition = node.node().condition();
                if (condition != null) {
                    var res = condition.apply(graph);
                    if (res instanceof GraphCondition.ConditionResult.Failed failed) {
                        errors.add(Map.entry(node.node(), failed));
                    }
                }
            }
            throw new NoneOfConditionalNodeMatches(errors);
        }
        var matchReasons = new ArrayList<Map.Entry<Node<?>, GraphCondition.ConditionResult.Matched>>(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var condition = node.node().condition();
            if (condition == null) {
                matchReasons.add(Map.entry(node.node(), new GraphCondition.ConditionResult.Matched("Node %s has no conditions".formatted(node.toString()))));
            } else {
                var res = condition.apply(graph);
                if (res instanceof GraphCondition.ConditionResult.Matched matched) {
                    matchReasons.add(Map.entry(node.node(), matched));
                }
            }
        }
        throw new MoreThanOneConditionalNodeMatches(matchReasons);
    }
}
