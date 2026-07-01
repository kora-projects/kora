package io.koraframework.application.graph;

public interface Wrapped<T> {

    T value();

    static <T> ValueOf<T> unwrap(ValueOf<Wrapped<T>> valueOf) {
        return valueOf.map(Wrapped::value);
    }
}
