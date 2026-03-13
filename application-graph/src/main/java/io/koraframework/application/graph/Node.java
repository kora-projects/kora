package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.NodeImpl;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.function.Function;

public sealed interface Node<T> permits NodeImpl {
    Type type();

    @Nullable
    Class<?> tag();

    @Nullable
    Function<Graph, GraphCondition.ConditionResult> condition();
}
