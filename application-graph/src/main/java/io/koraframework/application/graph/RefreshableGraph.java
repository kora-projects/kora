package io.koraframework.application.graph;

public interface RefreshableGraph extends Graph, Lifecycle {
    void refresh(Node<?> fromNode);
}
