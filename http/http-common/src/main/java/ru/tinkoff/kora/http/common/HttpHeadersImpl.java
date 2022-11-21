package ru.tinkoff.kora.http.common;


import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class HttpHeadersImpl implements HttpHeaders {
    private final Map<String, List<String>> values;

    @SafeVarargs
    @SuppressWarnings("varargs")
    HttpHeadersImpl(Map.Entry<String, List<String>>... entries) {
        this.values = Map.ofEntries(entries);
    }

    @Nullable
    @Override
    public String getFirst(String name) {
        var headerValues = this.values.get(name.toLowerCase());
        if (headerValues == null || headerValues.isEmpty()) {
            return null;
        }

        return headerValues.get(0);
    }

    @Override
    @Nullable
    public List<String> get(String name) {
        var value = this.values.get(name.toLowerCase());
        if (value == null) {
            return null;
        }
        return Collections.unmodifiableList(value);
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return this.values.entrySet().iterator();
    }
}
