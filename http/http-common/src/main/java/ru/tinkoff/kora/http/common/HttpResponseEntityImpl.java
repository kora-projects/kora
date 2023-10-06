package ru.tinkoff.kora.http.common;

import ru.tinkoff.kora.http.common.header.HttpHeaders;

record HttpResponseEntityImpl<T>(int code, MutableHttpHeaders headers, T body) implements HttpResponseEntity<T> {

    @Override
    public String toString() {
        return "HttpResponseEntity{code=" + code() + ", headers=" + HttpHeaders.toStringPlain(headers()) + '}';
    }
}
