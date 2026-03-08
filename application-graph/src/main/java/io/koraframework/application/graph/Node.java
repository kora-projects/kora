package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.NodeImpl;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;

public sealed interface Node<T> permits NodeImpl {
    Type type();

    @Nullable
    Class<?> tag();
}
