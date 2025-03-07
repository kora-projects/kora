package ru.tinkoff.kora.http.server.undertow;

import io.undertow.util.HeaderMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.AbstractHttpHeaders;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.*;

public class UndertowHttpHeaders extends AbstractHttpHeaders implements HttpHeaders {
    private final HeaderMap headerMap;

    public UndertowHttpHeaders(HeaderMap headerMap) {
        this.headerMap = headerMap;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        return this.headerMap.getFirst(headerName);
    }

    @Override
    public List<String> getAll(String headerName) {
        var headers = this.headerMap.get(headerName);
        if (headers == null) {
            return null;
        }
        return Collections.unmodifiableList(headers);
    }

    @Override
    public boolean has(String headerName) {
        return headerMap.contains(headerName);
    }

    @Override
    public int size() {
        return this.headerMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.headerMap.size() == 0;
    }

    @Override
    public Set<String> names() {
        var names = new HashSet<String>();
        for (var headerName : this.headerMap.getHeaderNames()) {
            names.add(headerName.toString().toLowerCase());
        }
        return names;
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var i = this.headerMap.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Map.Entry<String, List<String>> next() {
                var next = i.next();
                return Map.entry(next.getHeaderName().toString().toLowerCase(), next);
            }
        };
    }
}
