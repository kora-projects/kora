package ru.tinkoff.kora.application.graph;

public interface Lifecycle {
    void init() throws Exception;

    void release() throws Exception;
}
