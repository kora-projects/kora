package ru.tinkoff.kora.application.graph;

import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import java.lang.reflect.Type;

public sealed interface Node<T> permits NodeImpl {
    Node<T> valueOf();

    boolean isValueOf();

    Type type();

    Class<?>[] tags();
}
