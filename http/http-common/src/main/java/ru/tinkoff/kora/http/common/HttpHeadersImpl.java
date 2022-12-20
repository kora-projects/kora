package ru.tinkoff.kora.http.common;


import javax.annotation.Nullable;
import java.util.*;

class HttpHeadersImpl implements HttpHeaders {
    private final Map<String, List<String>> values;

    @SafeVarargs
    @SuppressWarnings("varargs")
    HttpHeadersImpl(Map.Entry<String, List<String>>... entries) {
        this.values = Map.ofEntries(entries);
    }

    HttpHeadersImpl(Map<String, List<String>> values) {
        this.values = values;
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
    public boolean has(String key) {
        return this.values.containsKey(key.toLowerCase());
    }

    @Override
    public HttpHeaders with(String key, String value) {
        var newValues = new HashMap<>(this.values);
        newValues.put(key.toLowerCase(), List.of(value));
        return new HttpHeadersImpl(newValues);
    }

    @Override
    public HttpHeaders without(String key) {
        var newValues = new HashMap<>(this.values);
        newValues.remove(key.toLowerCase());
        return new HttpHeadersImpl(newValues);
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(this.values.keySet());
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return this.values.entrySet().iterator();
    }
}
