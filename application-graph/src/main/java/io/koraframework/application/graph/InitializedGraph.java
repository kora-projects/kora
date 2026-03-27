package io.koraframework.application.graph;

public interface InitializedGraph extends RefreshableGraph {
    void init() throws Exception;

    void release() throws Exception;

}
