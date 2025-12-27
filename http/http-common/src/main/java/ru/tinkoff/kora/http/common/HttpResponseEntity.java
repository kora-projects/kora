package ru.tinkoff.kora.http.common;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

/**
 * <b>Русский</b>: Описывает HTTP ответ как сущность с метаинформацией о коде ответа и заголовках
 * <hr>
 * <b>English</b>: Describes an HTTP response as an entity with meta-information about the response code and headers
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpResponseEntity.of(200, "OK")
 * }
 * </pre>
 */
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
