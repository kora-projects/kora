package io.koraframework.resilient.common;

@FunctionalInterface
public interface ThrowableRunnable<E extends Throwable> {

    void run() throws E;
}
