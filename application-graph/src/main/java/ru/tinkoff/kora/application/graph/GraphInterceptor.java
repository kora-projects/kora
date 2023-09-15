package ru.tinkoff.kora.application.graph;

public interface GraphInterceptor<T> {
    T init(T value);

    T release(T value);
}
