package ru.tinkoff.kora.http.common.header;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * <b>Русский</b>: Описывает заголовки HTTP запроса/ответа, не мутирующий
 * <hr>
 * <b>English</b>: Describes HTTP request/response headers, immutable
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpHeaders.of("content-type", "application/json").toMutable()
 * }
 * </pre>
 */
public interface MutableHttpHeaders extends HttpHeaders {

    MutableHttpHeaders set(String key, Collection<String> value);

    default MutableHttpHeaders set(String key, Iterable<?> values) {
        Objects.requireNonNull(key);

        var list = new ArrayList<String>();
        for (var value : values) {
            list.add(Objects.toString(value));
        }
        return this.set(key, list);
    }

    default MutableHttpHeaders set(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        var list = new ArrayList<String>(1);
        list.add(value);
        return this.set(key, list);
    }

    default MutableHttpHeaders set(String key, Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return this.set(key, Objects.toString(value));
    }

    MutableHttpHeaders add(String key, Collection<String> value);

    default MutableHttpHeaders add(String key, String value) {
        return this.add(key, List.of(value));
    }

    MutableHttpHeaders remove(String key);
}
