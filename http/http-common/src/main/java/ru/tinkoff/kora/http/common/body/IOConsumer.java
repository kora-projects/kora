package ru.tinkoff.kora.http.common.body;

import java.io.IOException;

@FunctionalInterface
public interface IOConsumer<T> {

    void accept(T value) throws IOException;
}
