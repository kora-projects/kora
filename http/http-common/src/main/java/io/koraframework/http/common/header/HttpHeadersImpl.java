package io.koraframework.http.common.header;


import org.jspecify.annotations.Nullable;

import java.util.*;

public final class HttpHeadersImpl extends AbstractHttpHeaders implements MutableHttpHeaders {

    private Map<String, List<String>> values = Collections.emptyMap();

    public HttpHeadersImpl(HttpHeaders headers) {
        if (!headers.isEmpty()) {
            if (headers instanceof HttpHeadersImpl hi) {
                this.values = new LinkedHashMap<>(hi.values);
            } else {
                this.values = new LinkedHashMap<>(calculateHashMapCapacity(headers.size()));
                for (var e : headers) {
                    this.values.put(e.getKey().toLowerCase(Locale.ROOT), new ArrayList<>(e.getValue()));
                }
            }
        }
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public HttpHeadersImpl(Map.Entry<String, List<String>>... entries) {
        if (entries.length != 0) {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(entries.length));
            for (var entry : entries) {
                var key = entry.getKey().toLowerCase(Locale.ROOT);
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

    @Override
    public MutableHttpHeaders toMutable() {
        return this;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        var headerValues = this.values.get(headerName.toLowerCase(Locale.ROOT));
        if (headerValues == null || headerValues.isEmpty()) {
            return null;
        }

        return headerValues.get(0);
    }

    @Override
    @Nullable
    public List<String> getAll(String headerName) {
        var value = this.values.get(headerName.toLowerCase(Locale.ROOT));
        if (value == null) {
            return null;
        }
        return Collections.unmodifiableList(value);
    }

    @Override
    public boolean has(String headerName) {
        return this.values.containsKey(headerName.toLowerCase(Locale.ROOT));
    }

    @Override
    public MutableHttpHeaders set(String key, String value) {
        if (this.values.isEmpty()) {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(4));
        }

        Objects.requireNonNull(value);
        key = key.toLowerCase(Locale.ROOT);

        var valueList = new ArrayList<String>(1);
        valueList.add(value);

        this.values.put(key, valueList);
        return this;
    }

    @Override
    public MutableHttpHeaders add(String key, Collection<String> value) {
        if (this.values.isEmpty()) {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(4));
        }

        Objects.requireNonNull(value);
        key = key.toLowerCase(Locale.ROOT);

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
        if (this.values.isEmpty()) {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(4));
        }

        Objects.requireNonNull(value);
        key = key.toLowerCase(Locale.ROOT);

        var existing = this.values.computeIfAbsent(key, k -> new ArrayList<>(1));
        existing.add(value);
        return this;
    }

    @Override
    public MutableHttpHeaders set(String key, Collection<String> value) {
        if (this.values.isEmpty()) {
            this.values = new LinkedHashMap<>(calculateHashMapCapacity(4));
        }

        this.values.put(key.toLowerCase(Locale.ROOT), new ArrayList<>(value));
        return this;
    }

    @Override
    public MutableHttpHeaders remove(String key) {
        if (!this.values.isEmpty()) {
            this.values.remove(key.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public Set<String> names() {
        if (this.values.isEmpty()) {
            return Collections.emptySet();
        }
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
