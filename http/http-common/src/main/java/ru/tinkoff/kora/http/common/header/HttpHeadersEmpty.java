package ru.tinkoff.kora.http.common.header;


import org.jspecify.annotations.Nullable;

import java.util.*;

final class HttpHeadersEmpty implements HttpHeaders {

    static final HttpHeaders INSTANCE = new HttpHeadersEmpty();

    @Nullable
    @Override
    public String getFirst(String name) {
        return null;
    }

    @Nullable
    @Override
    public List<String> getAll(String name) {
        return null;
    }

    @Override
    public boolean has(String key) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Set<String> names() {
        return Collections.emptySet();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public String toString() {
        return "HttpHeadersEmpty{}";
    }
}
