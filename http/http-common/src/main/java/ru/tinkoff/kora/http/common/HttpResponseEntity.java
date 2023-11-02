package ru.tinkoff.kora.http.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

public interface HttpResponseEntity<T> {

    int code();

    MutableHttpHeaders headers();

    @Nullable
    T body();

    static <T> HttpResponseEntity<T> of(int code, T body) {
        return new HttpResponseEntityImpl<>(code, HttpHeaders.of(), body);
    }

    static <T> HttpResponseEntity<T> of(int code, MutableHttpHeaders headers, T body) {
        return new HttpResponseEntityImpl<>(code, headers, body);
    }
}
