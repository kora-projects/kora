package ru.tinkoff.kora.http.common.header;

import jakarta.annotation.Nullable;

import java.util.*;

/**
 * <b>Русский</b>: Описывает заголовки HTTP запроса/ответа, не мутирующий
 * <hr>
 * <b>English</b>: Describes HTTP request/response headers, immutable
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpHeaders.of("content-type", "application/json")
 * }
 * </pre>
 */
public interface HttpHeaders extends Iterable<Map.Entry<String, List<String>>> {

    /**
     * @return <b>Русский</b>: Возвращает первое найденное значение по имени переданного заголовка либо <i>null</i> если таковой заголовок отсутствует
     * <hr>
     * <b>English</b>: Returns the first value found by the name of the passed header or <i>null</i> if no header is present
     */
    @Nullable
    String getFirst(String headerName);

    /**
     * @return <b>Русский</b>: Возвращает все значения по имени переданного заголовка либо <i>null</i> если таковые заголовки отсутствуют
     * <hr>
     * <b>English</b>: Returns all values by name of the passed header or <i>null</i> if no such headers are present
     */
    @Nullable
    List<String> getAll(String headerName);

    boolean has(String headerName);

    default boolean isEmpty() {
        return size() == 0;
    }

    int size();

    /**
     * @return <b>Русский</b>: Возвращает все имена заголовков
     * <hr>
     * <b>English</b>: Returns all header names
     */
    Set<String> names();

    default MutableHttpHeaders toMutable() {
        return new HttpHeadersImpl(this);
    }

    static HttpHeaders empty() {
        return HttpHeadersEmpty.INSTANCE;
    }

    static MutableHttpHeaders of() {
        return new HttpHeadersImpl();
    }

    static MutableHttpHeaders of(Map<String, List<String>> headers) {
        var headersResult = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(headers.size()));
        for (var entry : headers.entrySet()) {
            headersResult.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        return new HttpHeadersImpl(headersResult);
    }

    static MutableHttpHeaders ofPlain(Map<String, String> headers) {
        var headersResult = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(headers.size()));
        for (var entry : headers.entrySet()) {
            var headerValue = new ArrayList<String>(1);
            headerValue.add(entry.getValue());
            headersResult.put(entry.getKey().toLowerCase(), headerValue);
        }

        return new HttpHeadersImpl(headersResult);
    }

    @SafeVarargs
    static MutableHttpHeaders of(Map.Entry<String, List<String>>... entries) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(entries.length));
        for (var entry : entries) {
            headers.put(entry.getKey().toLowerCase(), new ArrayList<>(entry.getValue()));
        }

        return new HttpHeadersImpl(headers);
    }

    @SafeVarargs
    static MutableHttpHeaders ofPlain(Map.Entry<String, String>... entries) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(entries.length));
        for (var entry : entries) {
            var headerValue = new ArrayList<String>(1);
            headerValue.add(entry.getValue());
            headers.put(entry.getKey().toLowerCase(), headerValue);
        }

        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, String v1) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);

        return headers;
    }

    static MutableHttpHeaders of(String k1, List<String> v1) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);

        return headers;
    }

    static MutableHttpHeaders of(String k1, String v1, String k2, String v2) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);

        return headers;
    }

    static MutableHttpHeaders of(String k1, List<String> v1, String k2, List<String> v2) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);

        return headers;
    }

    static MutableHttpHeaders of(String k1, String v1, String k2, String v2, String k3, String v3) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);
        headers.add(k3, v3);

        return headers;
    }

    static MutableHttpHeaders of(String k1, List<String> v1, String k2, List<String> v2, String k3, List<String> v3) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);
        headers.add(k3, v3);

        return headers;
    }

    static MutableHttpHeaders of(String k1, String v1,
                                 String k2, String v2,
                                 String k3, String v3,
                                 String k4, String v4) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);
        headers.add(k3, v3);
        headers.add(k4, v4);

        return headers;
    }

    static MutableHttpHeaders of(String k1, List<String> v1,
                                 String k2, List<String> v2,
                                 String k3, List<String> v3,
                                 String k4, List<String> v4) {
        var headers = new HttpHeadersImpl();

        headers.set(k1, v1);
        headers.add(k2, v2);
        headers.add(k3, v3);
        headers.add(k4, v4);

        return headers;
    }

    static String toString(HttpHeaders headers) {
        var sb = new StringBuilder();
        for (var entry : headers) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }

            sb.append(entry.getKey());
            boolean first = true;
            for (var val : entry.getValue()) {
                if (first) {
                    first = false;
                    sb.append(": ");
                } else {
                    sb.append(", ");
                }
                sb.append(val);
            }
        }
        return sb.toString();
    }

    static String toStringPlain(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return "{}";
        }

        var sb = new StringBuilder();
        boolean firstKey = true;
        for (var entry : headers) {
            if (firstKey) {
                firstKey = false;
            } else {
                sb.append(", ");
            }

            sb.append(entry.getKey()).append(": [");
            boolean first = true;
            for (var val : entry.getValue()) {
                if (first) {
                    first = false;
                    sb.append(": ");
                } else {
                    sb.append(", ");
                }
                sb.append(val);
            }

            sb.append("]");
        }
        return sb.toString();
    }
}
