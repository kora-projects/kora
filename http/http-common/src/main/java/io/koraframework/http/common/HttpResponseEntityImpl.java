package io.koraframework.http.common;

import io.koraframework.http.common.header.HttpHeaders;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.header.MutableHttpHeaders;

record HttpResponseEntityImpl<T>(int code, HttpHeaders headers, @Nullable T body) implements HttpResponseEntity<T> {

    @Override
    public String toString() {
        return "HttpResponseEntity{code=" + code() + '}';
    }
}
