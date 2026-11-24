package io.koraframework.http.server.netty.handler;

import io.netty.handler.codec.http.*;
import io.opentelemetry.context.propagation.*;

public final class NettyHttpHeaderMapExchange implements TextMapGetter<HttpHeaders>, TextMapSetter<HttpHeaders> {
    public static final NettyHttpHeaderMapExchange INSTANCE = new NettyHttpHeaderMapExchange();

    @Override
    public Iterable<String> keys(HttpHeaders carrier) {
        return carrier.names();
    }

    @Override
    public String get(HttpHeaders carrier, String key) {
        return carrier.get(key);
    }

    @Override
    public void set(HttpHeaders carrier, String key, String value) {
        carrier.set(key, value);
    }
}
