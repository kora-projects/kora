package ru.tinkoff.kora.common.util;

@FunctionalInterface
public interface Configurer<T> {
    T configure(T t);
}
