package io.koraframework.application.graph;

public interface Lifecycle {
    void init() throws Exception;

    void release() throws Exception;
}
