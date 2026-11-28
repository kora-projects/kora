package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;
import org.jspecify.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public final class NodeImpl<T> implements Node<T> {
    public final ApplicationGraphDraw graphDraw;
    public final int index;

    public NodeImpl(ApplicationGraphDraw graphDraw, int index) {
        this.graphDraw = graphDraw;
        this.index = index;
    }

    @Override
    public Type type() {
        return graphDraw.getNodeType(index);
    }

    @Nullable
    @Override
    public Class<?> tag() {
        return graphDraw.getNodeTag(index);
    }

    @Nullable
    @Override
    public Function<Graph, GraphCondition.ConditionResult> condition() {
        return graphDraw.getNodeCondition(index);
    }

    public List<Node<?>> interceptors() {
        return graphDraw.getNodeInterceptors(index);
    }

    public List<Node<?>> createDependencies() {
        return graphDraw.getNodeCreateDependencies(index);
    }

    public List<Node<?>> refreshDependencies() {
        return graphDraw.getNodeRefreshDependencies(index);
    }

    @Override
    public String toString() {
        int depSize = graphDraw.getCreateDependenciesCount(index);
        Type type = type();
        Class<?> tag = tag();
        if (tag == null) {
            return "[#" + index + "] " + type + " (" + depSize + " dependencies)";
        } else {
            return "[#" + index + "] " + type + " (@Tag(" + tag + ")) (" + depSize + " dependencies)";
        }
    }
}
