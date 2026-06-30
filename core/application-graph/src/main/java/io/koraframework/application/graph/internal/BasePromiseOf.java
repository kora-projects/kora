package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.Graph;
import io.koraframework.application.graph.PromiseOf;
import org.jspecify.annotations.Nullable;

public abstract class BasePromiseOf<T> implements PromiseOf<T> {
    @Nullable
    public volatile Graph graph;
}
