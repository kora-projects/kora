package ru.tinkoff.kora.http.client.jdk;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.AbstractHttpHeaders;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.*;

public class JdkHttpClientHeaders extends AbstractHttpHeaders implements HttpHeaders {
    private final Map<String, List<String>> headers;

    public JdkHttpClientHeaders(java.net.http.HttpHeaders headers) {
        this.headers = headers.map();
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        var headers = this.headers.get(headerName);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.get(0);
    }

    @Nullable
    @Override
    public List<String> getAll(String headerName) {
        return this.headers.get(headerName);
    }

    @Override
    public int size() {
        return this.headers.size();
    }

    @Override
    public boolean has(String headerName) {
        return this.headers.containsKey(headerName);
    }

    @Override
    public boolean isEmpty() {
        return this.headers.isEmpty();
    }

    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(this.headers.keySet());
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return this.headers.entrySet().iterator();
    }
}
