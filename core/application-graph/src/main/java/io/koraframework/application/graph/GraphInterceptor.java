package io.koraframework.application.graph;

public interface GraphInterceptor<T> {

    T afterInit(T value);

    T beforeRelease(T value);
}
