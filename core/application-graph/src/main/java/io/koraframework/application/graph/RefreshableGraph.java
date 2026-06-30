package io.koraframework.application.graph;

public interface RefreshableGraph extends Graph {
    void refresh(Node<?> fromNode);
}
