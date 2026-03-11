package io.koraframework.common.util;

@FunctionalInterface
public interface Configurer<T> {
    T configure(T t);
}
