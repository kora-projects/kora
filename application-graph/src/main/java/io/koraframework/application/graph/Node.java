package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.NodeImpl;

import java.lang.reflect.Type;

public sealed interface Node<T> permits NodeImpl {
    Node<T> valueOf();

    boolean isValueOf();

    Type type();

    Class<?> tag();
}
