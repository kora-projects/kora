package io.koraframework.http.server.undertow.request;

import io.undertow.util.HeaderMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.header.AbstractHttpHeaders;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.*;

@NullMarked
public final class UndertowHttpHeaders extends AbstractHttpHeaders implements HttpHeaders {

    private final HeaderMap headerMap;
    @Nullable
    private volatile Set<String> names;
    @Nullable
    private volatile List<Map.Entry<String, List<String>>> entries;

    public UndertowHttpHeaders(HeaderMap headerMap) {
        this.headerMap = headerMap;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        return this.headerMap.getFirst(headerName);
    }

    @Override
    @Nullable
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
        var names = this.names;
        if (names != null) {
            return names;
        }
        names = new LinkedHashSet<>();
        for (var headerName : this.headerMap.getHeaderNames()) {
            names.add(headerName.toString().toLowerCase());
        }
        return this.names = Collections.unmodifiableSet(names);
    }

    @Override
    @Nullable
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var entries = this.entries;
        if (entries != null) {
            return entries.iterator();
        }
        entries = new ArrayList<>(this.headerMap.size());
        for (var header : this.headerMap) {
            entries.add(Map.entry(header.getHeaderName().toString().toLowerCase(), Collections.unmodifiableList(header)));
        }
        return (this.entries = Collections.unmodifiableList(entries)).iterator();
    }

    @Override
    public String toString() {
        return headerMap.toString();
    }
}
