package io.koraframework.http.server.netty.request;

import io.koraframework.http.common.header.AbstractHttpHeaders;
import io.koraframework.http.common.header.HttpHeaders;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@NullMarked
public final class NettyHttpHeaders extends AbstractHttpHeaders implements HttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders nettyHeaders;
    @Nullable
    private volatile Set<String> names;
    @Nullable
    private volatile List<Map.Entry<String, List<String>>> entries;

    public NettyHttpHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders) {
        this.nettyHeaders = nettyHeaders;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        return nettyHeaders.get(headerName);
    }

    @Override
    @Nullable
    public List<String> getAll(String headerName) {
        List<String> all = nettyHeaders.getAll(headerName);
        if (all.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableList(all);
    }

    @Override
    public boolean has(String headerName) {
        return nettyHeaders.contains(headerName);
    }

    @Override
    public int size() {
        return nettyHeaders.size();
    }

    @Override
    public boolean isEmpty() {
        return nettyHeaders.isEmpty();
    }

    @Override
    public Set<String> names() {
        var names = this.names;
        if (names != null) {
            return names;
        }

        var nettyNames = nettyHeaders.names();
        var computedNames = new LinkedHashSet<String>(nettyNames.size());
        for (CharSequence headerName : nettyNames) {
            computedNames.add(headerName.toString().toLowerCase(Locale.ROOT));
        }

        names = Collections.unmodifiableSet(computedNames);
        this.names = names;
        return names;
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var entries = this.entries;
        if (entries != null) {
            return entries.iterator();
        }

        var result = new LinkedHashMap<String, List<String>>(nettyHeaders.size());
        var headersIter = nettyHeaders.iteratorCharSequence();

        while (headersIter.hasNext()) {
            var entry = headersIter.next();
            var lowerKey = entry.getKey().toString().toLowerCase(Locale.ROOT);
            var value = entry.getValue().toString();

            var list = result.get(lowerKey);
            if (list == null) {
                list = new ArrayList<>(1);
                result.put(lowerKey, list);
            }
            list.add(value);
        }

        var computedEntries = new ArrayList<Map.Entry<String, List<String>>>(result.size());
        for (var entry : result.entrySet()) {
            computedEntries.add(Map.entry(entry.getKey(), Collections.unmodifiableList(entry.getValue())));
        }
        entries = Collections.unmodifiableList(computedEntries);
        this.entries = entries;
        return entries.iterator();
    }

    @Override
    public String toString() {
        return nettyHeaders.toString();
    }
}
