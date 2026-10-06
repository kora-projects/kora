package io.koraframework.http.client.common.request;

import io.koraframework.http.client.common.util.EncoderUtils;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.HttpHeadersImpl;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpClientRequestBuilderImpl implements HttpClientRequestBuilder {
    private final String method;
    private final String uriTemplate;
    private HttpHeaders headers;
    private boolean headersOwned;

    @Nullable
    private final URI fromUri;
    private final List<PathParam> pathParams = new ArrayList<>();
    private final List<QueryParam> queryParams = new ArrayList<>();
    private HttpBodyOutput body = HttpBody.empty();
    @Nullable
    private Duration requestTimeout;

    public HttpClientRequestBuilderImpl(String method, String uriTemplate) {
        this.method = method;
        this.uriTemplate = uriTemplate;
        this.headers = HttpHeaders.empty();
        this.headersOwned = true;
        this.fromUri = null;
    }

    public HttpClientRequestBuilderImpl(HttpClientRequest httpClientRequest) {
        this.method = httpClientRequest.method();
        this.uriTemplate = httpClientRequest.uriTemplate();
        var uri = httpClientRequest.uri();
        var rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            for (var pair : rawQuery.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                var eq = pair.indexOf('=');
                this.queryParams.add(eq < 0
                    ? new QueryParam(pair, null, true)
                    : new QueryParam(pair.substring(0, eq), pair.substring(eq + 1), true));
            }
            var uriString = uri.toString();
            uri = URI.create(uriString.substring(0, uriString.indexOf('?')));
        }
        this.fromUri = uri;
        this.headers = httpClientRequest.headers();
        this.body = httpClientRequest.body();
        this.requestTimeout = httpClientRequest.requestTimeout();
    }

    @Override
    public HttpClientRequest build() {
        var resolved = resolveUri(this.fromUri, this.uriTemplate, this.pathParams, this.queryParams);

        return new SimpleHttpClientRequest(
            this.method,
            resolved.uri,
            resolved.uriTemplate,
            this.headers,
            this.body,
            this.requestTimeout
        );
    }

    @Override
    public HttpClientRequestBuilder pathParam(String name, String value) {
        boolean found = false;
        for (int i = 0; i < this.pathParams.size(); i++) {
            var entry = this.pathParams.get(i);
            if (entry.name().equals(name)) {
                pathParams.set(i, new PathParam(name, value));
                found = true;
                break;
            }
        }

        if (!found) {
            this.pathParams.add(new PathParam(name, value));
        }
        return this;
    }

    @Override
    public HttpClientRequestBuilder queryParam(String name) {
        this.queryParams.add(new QueryParam(name, null, false));
        return this;
    }

    @Override
    public HttpClientRequestBuilder queryParam(String name, String value) {
        this.queryParams.add(new QueryParam(name, value, false));
        return this;
    }

    @Override
    public HttpClientRequestBuilder queryParamRemove(String name) {
        this.queryParams.removeIf(q -> (q.encoded() ? URLDecoder.decode(q.name(), UTF_8) : q.name()).equals(name));
        return this;
    }

    @Override
    public HttpClientRequestBuilder header(String name, String value) {
        this.mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpClientRequestBuilder header(String name, List<String> value) {
        this.mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpClientRequestBuilder headerRemove(String name) {
        if (this.headers.isEmpty()) {
            return this;
        }
        this.mutableHeaders().remove(name);
        return this;
    }

    @Override
    public HttpClientRequestBuilder requestTimeout(int timeoutMillis) {
        this.requestTimeout = Duration.ofMillis(timeoutMillis);
        return this;
    }

    @Override
    public HttpClientRequestBuilder requestTimeout(Duration timeout) {
        this.requestTimeout = timeout;
        return this;
    }

    @Override
    public HttpClientRequestBuilder body(HttpBodyOutput body) {
        this.body = body;
        return this;
    }

    private MutableHttpHeaders mutableHeaders() {
        if (this.headersOwned && this.headers instanceof MutableHttpHeaders mutableHeaders) {
            return mutableHeaders;
        }
        var mutableHeaders = this.headersOwned
            ? this.headers.toMutable()
            : new HttpHeadersImpl(this.headers);
        this.headers = mutableHeaders;
        this.headersOwned = true;
        return mutableHeaders;
    }

    private record PathParam(String name, String value) {}

    private record QueryParam(String name, @Nullable String value, boolean encoded) {}

    private record ResolvedUri(URI uri, String uriTemplate) {}

    private static ResolvedUri resolveUri(@Nullable URI fromUri,
                                          String uriTemplate,
                                          List<PathParam> pathParams,
                                          List<QueryParam> queryParams) {
        if (pathParams.isEmpty() && queryParams.isEmpty()) {
            return fromUri != null
                ? buildResolvedUri(fromUri, uriTemplate, uriTemplate, fromUri)
                : buildResolvedUri(null, uriTemplate, uriTemplate, URI.create(uriTemplate));
        }
        var template = fromUri != null
            ? fromUri.toString()
            : uriTemplate;
        for (var i = pathParams.listIterator(pathParams.size()); i.hasPrevious(); ) {
            var entry = i.previous();
            template = template.replace("{" + entry.name() + "}", EncoderUtils.encode(entry.value(), UTF_8, true));
        }

        if (queryParams.isEmpty()) {
            return buildResolvedUri(fromUri, uriTemplate, template, URI.create(template));
        }

        var b = new UriQueryBuilder(true, false);
        for (var entry : queryParams) {
            if (entry.encoded()) {
                if (entry.value() == null) {
                    b.unsafeAdd(entry.name());
                } else {
                    b.unsafeAdd(entry.name(), entry.value());
                }
            } else if (entry.value() == null) {
                b.add(entry.name());
            } else {
                b.add(entry.name(), entry.value);
            }
        }
        URI uri = URI.create(template + b.build());
        return buildResolvedUri(fromUri, uriTemplate, template, uri);
    }

    private static ResolvedUri buildResolvedUri(URI fromUri, String uriTemplate, String template, URI uri) {
        String resultTemplate = uriTemplate;
        if (fromUri == null && resultTemplate.startsWith("http")) {
            var pathStart = template.lastIndexOf(uri.getRawPath());
            if (pathStart > 0) {
                resultTemplate = resultTemplate.substring(pathStart);
            }
        }

        return new ResolvedUri(uri, resultTemplate);
    }

    @Override
    public String toString() {
        return "HttpClientRequestBuilder{method=" + method +
               ", path=" + uriTemplate +
               ", pathParams=" + pathParams +
               ", queryParams=" + queryParams +
               ", headers=" + headers +
               ", body=" + body +
               ", requestTimeout=" + requestTimeout +
               '}';
    }
}
