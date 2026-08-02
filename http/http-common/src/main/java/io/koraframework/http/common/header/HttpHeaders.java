package io.koraframework.http.common.header;

import org.jspecify.annotations.Nullable;

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
        if (headers.isEmpty()) {
            return new HttpHeadersImpl();
        }
        var headersResult = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(headers.size()));
        for (var entry : headers.entrySet()) {
            headersResult.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }

        return new HttpHeadersImpl(headersResult);
    }

    static MutableHttpHeaders ofPlain(Map<String, String> headers) {
        if (headers.isEmpty()) {
            return new HttpHeadersImpl();
        }
        var headersResult = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(headers.size()));
        for (var entry : headers.entrySet()) {
            var headerValue = new ArrayList<String>(1);
            headerValue.add(entry.getValue());
            headersResult.put(entry.getKey().toLowerCase(Locale.ROOT), headerValue);
        }

        return new HttpHeadersImpl(headersResult);
    }

    @SafeVarargs
    static MutableHttpHeaders of(Map.Entry<String, List<String>>... entries) {
        if (entries.length == 0) {
            return new HttpHeadersImpl();
        }
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(entries.length));
        for (var entry : entries) {
            headers.put(entry.getKey().toLowerCase(Locale.ROOT), new ArrayList<>(entry.getValue()));
        }

        return new HttpHeadersImpl(headers);
    }

    @SafeVarargs
    static MutableHttpHeaders ofPlain(Map.Entry<String, String>... entries) {
        if (entries.length == 0) {
            return new HttpHeadersImpl();
        }
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(entries.length));
        for (var entry : entries) {
            var headerValue = new ArrayList<String>(1);
            headerValue.add(entry.getValue());
            headers.put(entry.getKey().toLowerCase(Locale.ROOT), headerValue);
        }

        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, String v1) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(1));
        setHeader(headers, k1, v1);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, List<String> v1) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(1));
        setHeader(headers, k1, v1);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, String v1, String k2, String v2) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(2));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, List<String> v1, String k2, List<String> v2) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(2));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, String v1, String k2, String v2, String k3, String v3) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(3));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        addHeader(headers, k3, v3);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, List<String> v1, String k2, List<String> v2, String k3, List<String> v3) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(3));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        addHeader(headers, k3, v3);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, String v1,
                                 String k2, String v2,
                                 String k3, String v3,
                                 String k4, String v4) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(4));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        addHeader(headers, k3, v3);
        addHeader(headers, k4, v4);
        return new HttpHeadersImpl(headers);
    }

    static MutableHttpHeaders of(String k1, List<String> v1,
                                 String k2, List<String> v2,
                                 String k3, List<String> v3,
                                 String k4, List<String> v4) {
        var headers = new LinkedHashMap<String, List<String>>(HttpHeadersImpl.calculateHashMapCapacity(4));
        setHeader(headers, k1, v1);
        addHeader(headers, k2, v2);
        addHeader(headers, k3, v3);
        addHeader(headers, k4, v4);
        return new HttpHeadersImpl(headers);
    }

    private static void setHeader(Map<String, List<String>> headers, String key, String value) {
        Objects.requireNonNull(value);
        var values = new ArrayList<String>(1);
        values.add(value);
        headers.put(key.toLowerCase(Locale.ROOT), values);
    }

    private static void setHeader(Map<String, List<String>> headers, String key, List<String> value) {
        Objects.requireNonNull(value);
        headers.put(key.toLowerCase(Locale.ROOT), new ArrayList<>(value));
    }

    private static void addHeader(Map<String, List<String>> headers, String key, String value) {
        Objects.requireNonNull(value);
        headers.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new ArrayList<>(1)).add(value);
    }

    private static void addHeader(Map<String, List<String>> headers, String key, List<String> value) {
        Objects.requireNonNull(value);
        headers.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new ArrayList<>(value.size())).addAll(value);
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
