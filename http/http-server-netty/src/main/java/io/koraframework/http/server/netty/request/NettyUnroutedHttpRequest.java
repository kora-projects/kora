package io.koraframework.http.server.netty.request;

import io.koraframework.http.common.body.*;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.router.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import org.jspecify.annotations.*;

import java.util.*;

public class NettyUnroutedHttpRequest implements UnroutedHttpRequest {

    private static final AsciiString X_FORWARDED_PROTO = AsciiString.cached("x-forwarded-proto");

    protected final HttpRequest nettyRequest;
    private final String method;
    private final String path;
    private final long startTime;
    private final Map<String, List<String>> queryParams;
    private final HttpBodyInput body;
    @Nullable
    private NettyHttpHeaders headers;

    public NettyUnroutedHttpRequest(HttpRequest nettyRequest, @Nullable NettyRequestHttpBody streamingBody) {
        this.nettyRequest = nettyRequest;
        this.method = nettyRequest.method().name();
        this.startTime = System.nanoTime();
        var uri = nettyRequest.uri();
        int queryIdx = uri.indexOf('?');
        if (queryIdx == -1) {
            this.path = uri.indexOf('%') == -1 ? uri : QueryStringDecoder.decodeComponent(uri);
            this.queryParams = Map.of();
        } else {
            var decoder = new QueryStringDecoder(uri);
            this.path = decoder.path();
            var params = decoder.parameters();
            this.queryParams = params.isEmpty() ? Map.of() : Collections.unmodifiableMap(params);
        }
        this.body = streamingBody != null ? streamingBody : HttpBody.empty();
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String host() {
        String host = nettyRequest.headers().get(HttpHeaderNames.HOST);
        return host != null ? host : "localhost";
    }

    @Override
    public String scheme() {
        String forwardedProto = nettyRequest.headers().get(X_FORWARDED_PROTO);
        if (forwardedProto == null || "http".equalsIgnoreCase(forwardedProto)) {
            return "http";
        }
        if ("https".equalsIgnoreCase(forwardedProto)) {
            return "https";
        }
        return forwardedProto.toLowerCase(Locale.ROOT);
    }

    @Override
    public HttpHeaders headers() {
        var h = headers;
        if (h == null) {
            h = new NettyHttpHeaders(nettyRequest.headers());
            headers = h;
        }
        return h;
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    @Override
    public HttpBodyInput body() {
        return body;
    }

    @Override
    public long requestStartTimeInNanos() {
        return startTime;
    }

    @Override
    public String toString() {
        return nettyRequest.toString();
    }
}
