package ru.tinkoff.kora.http.common.header;


import jakarta.annotation.Nullable;

import java.util.*;

public final class HttpHeadersImpl extends AbstractHttpHeaders implements MutableHttpHeaders {
    private final Map<String, List<String>> values;

    public HttpHeadersImpl(HttpHeaders headers) {
        if (headers.isEmpty()) {
            this.values = new LinkedHashMap<>();
        } else {
            if (headers instanceof HttpHeadersImpl hi) {
                this.values = new LinkedHashMap<>(hi.values);
            } else {
                this.values = new LinkedHashMap<>(calculateHashMapCapacity(headers.size()));
                for (var e : headers) {
                    this.values.put(e.getKey().toLowerCase(), new ArrayList<>(e.getValue()));
                }
            }
        }
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public HttpHeadersImpl(Map.Entry<String, List<String>>... entries) {
        if (entries.length == 0) {
            this.values = new LinkedHashMap<>();
        } else {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(entries.length));
            for (var entry : entries) {
                var key = entry.getKey().toLowerCase();
                var list = this.values.get(key);
                if (list == null) {
                    this.values.put(key, new ArrayList<>(entry.getValue()));
                } else {
                    list.addAll(entry.getValue());
                }
            }
        }
    }

    HttpHeadersImpl(Map<String, List<String>> values) {
        this.values = values;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        var headerValues = this.values.get(headerName.toLowerCase());
        if (headerValues == null || headerValues.isEmpty()) {
            return null;
        }

        return headerValues.get(0);
    }

    @Override
    @Nullable
    public List<String> getAll(String headerName) {
        var value = this.values.get(headerName.toLowerCase());
        if (value == null) {
            return null;
        }
        return Collections.unmodifiableList(value);
    }

    @Override
    public boolean has(String headerName) {
        return this.values.containsKey(headerName.toLowerCase());
    }

    @Override
    public MutableHttpHeaders set(String key, String value) {
        Objects.requireNonNull(value);
        key = key.toLowerCase();

        var valueList = new ArrayList<String>(1);
        valueList.add(value);

        this.values.put(key, valueList);
        return this;
    }

    @Override
    public MutableHttpHeaders add(String key, Collection<String> value) {
        Objects.requireNonNull(value);
        key = key.toLowerCase();

        var existing = this.values.get(key);
        if (existing == null) {
            this.values.put(key, new ArrayList<>(value));
        } else {
            existing.addAll(value);
        }
        return this;
    }

    @Override
    public MutableHttpHeaders add(String key, String value) {
        Objects.requireNonNull(value);
        key = key.toLowerCase();

        var existing = this.values.computeIfAbsent(key, k -> new ArrayList<>(1));
        existing.add(value);
        return this;
    }

    @Override
    public MutableHttpHeaders set(String key, Collection<String> value) {
        this.values.put(key.toLowerCase(), new ArrayList<>(value));
        return this;
    }

    @Override
    public MutableHttpHeaders remove(String key) {
        this.values.remove(key.toLowerCase());
        return this;
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

    static int calculateHashMapCapacity(int numMappings) {
        return (int) Math.ceil(numMappings / (double) 0.75f);
    }
}
