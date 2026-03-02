package ru.tinkoff.kora.http.common;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

record HttpResponseEntityImpl<T>(int code, MutableHttpHeaders headers, @Nullable T body) implements HttpResponseEntity<T> {

    @Override
    public String toString() {
        return "HttpResponseEntity{code=" + code() + '}';
    }
}
