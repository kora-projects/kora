package io.koraframework.resilient.common;

@FunctionalInterface
public interface ThrowableCallable<T, E extends Throwable> {

    T call() throws E;
}
