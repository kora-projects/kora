package ru.tinkoff.kora.http.common;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface HttpHeaders extends Iterable<Map.Entry<String, List<String>>> {
    HttpHeaders EMPTY = new HttpHeadersImpl();

    @Nullable
    String getFirst(String name);

    @Nullable
    List<String> get(String name);

    int size();


    default HttpHeaders with(String key, String value) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map.Entry<String, List<String>>[] entries = new Map.Entry[this.size() + 1];
        var i = 0;
        for (var stringListEntry : this) {
            entries[i++] = stringListEntry;
        }
        entries[entries.length - 1] = Map.entry(key.toLowerCase(), List.of(value));

        return new HttpHeadersImpl(entries);
    }

    @SafeVarargs
    static HttpHeaders of(Map.Entry<String, List<String>>... entries) {
        return new HttpHeadersImpl(entries);
    }

    static HttpHeaders of() {
        return EMPTY;
    }

    static HttpHeaders of(String k1, String v1) {
        return new HttpHeadersImpl(Map.entry(k1.toLowerCase(), List.of(v1)));
    }

    static HttpHeaders of(String k1, String v1, String k2, String v2) {
        return new HttpHeadersImpl(
            Map.entry(k1.toLowerCase(), List.of(v1)),
            Map.entry(k2.toLowerCase(), List.of(v2))
        );
    }

    static HttpHeaders of(String k1, String v1, String k2, String v2, String k3, String v3) {
        return new HttpHeadersImpl(
            Map.entry(k1.toLowerCase(), List.of(v1)),
            Map.entry(k2.toLowerCase(), List.of(v2)),
            Map.entry(k3.toLowerCase(), List.of(v3))
        );
    }

    static HttpHeaders of(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4) {
        return new HttpHeadersImpl(
            Map.entry(k1.toLowerCase(), List.of(v1)),
            Map.entry(k2.toLowerCase(), List.of(v2)),
            Map.entry(k3.toLowerCase(), List.of(v3)),
            Map.entry(k4.toLowerCase(), List.of(v4))
        );
    }

    static HttpHeaders of(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4, String k5, String v5) {
        return new HttpHeadersImpl(
            Map.entry(k1.toLowerCase(), List.of(v1)),
            Map.entry(k2.toLowerCase(), List.of(v2)),
            Map.entry(k3.toLowerCase(), List.of(v3)),
            Map.entry(k4.toLowerCase(), List.of(v4)),
            Map.entry(k5.toLowerCase(), List.of(v5))
        );
    }

    static String toString(HttpHeaders headers) {
        var sb = new StringBuilder();
        for (var entry : headers) {
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
            sb.append("\n");
        }
        return sb.toString();
    }
}
