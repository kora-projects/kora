package io.koraframework.common;

@FunctionalInterface
public interface Configurer<T> {

    T configure(T t);
}
