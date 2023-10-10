package ru.tinkoff.kora.http.client.common.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpClientRequestBuilderImpl implements HttpClientRequestBuilder {
    private final String method;
    private final String uriTemplate;
    private final MutableHttpHeaders headers;

    @Nullable
    private final URI fromUri;
    private final List<TemplateParam> templateParams = new ArrayList<>();
    private final List<QueryParam> queryParams = new ArrayList<>();
    private HttpBodyOutput body = HttpBody.empty();
    @Nullable
    private Duration requestTimeout;

    public HttpClientRequestBuilderImpl(String method, String uriTemplate) {
        this.method = method;
        this.uriTemplate = uriTemplate;
        this.headers = HttpHeaders.of();
        this.fromUri = null;
    }

    public HttpClientRequestBuilderImpl(HttpClientRequest httpClientRequest) {
        this.method = httpClientRequest.method();
        this.uriTemplate = httpClientRequest.uriTemplate();
        this.fromUri = httpClientRequest.uri();
        this.headers = httpClientRequest.headers().toMutable();
        this.body = httpClientRequest.body();
        this.requestTimeout = httpClientRequest.requestTimeout();
    }

    @Override
    public HttpClientRequest build() {
        var uri = resolveUri(this.fromUri, this.uriTemplate, this.templateParams, this.queryParams);

        return new DefaultHttpClientRequest(
            this.method,
            uri,
            this.uriTemplate,
            this.headers,
            this.body,
            this.requestTimeout
        );
    }

    @Override
    public HttpClientRequestBuilder templateParam(String name, String value) {
        this.templateParams.add(new TemplateParam(name, value));

        return this;
    }

    @Override
    public HttpClientRequestBuilder queryParam(String name) {
        this.queryParams.add(new QueryParam(name, null));

        return this;
    }

    @Override
    public HttpClientRequestBuilder queryParam(String name, String value) {
        this.queryParams.add(new QueryParam(name, value));

        return this;
    }

    @Override
    public HttpClientRequestBuilder header(String name, String value) {
        this.headers.set(name, value);

        return this;
    }

    @Override
    public HttpClientRequestBuilder header(String name, List<String> value) {
        this.headers.set(name, value);

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

    private record TemplateParam(String name, String value) {}

    private record QueryParam(String name, @Nullable String value) {}

    private static URI resolveUri(@Nullable URI fromUri, String uriTemplate, List<TemplateParam> templateParams, List<QueryParam> queryParams) {
        if (templateParams.isEmpty() && queryParams.isEmpty()) {
            return fromUri != null
                ? fromUri
                : URI.create(uriTemplate);
        }
        var template = fromUri != null
            ? fromUri.toString()
            : uriTemplate;
        for (var i = templateParams.listIterator(templateParams.size()); i.hasPrevious(); ) {
            var entry = i.previous();
            template = template.replace("{" + entry.name() + "}", URLEncoder.encode(entry.value(), UTF_8));
        }

        if (queryParams.isEmpty()) {
            return URI.create(template);
        }

        var noQMarK = fromUri != null && fromUri.getRawQuery() != null;
        var amp = noQMarK && !fromUri.getRawQuery().isBlank();
        var b = new UriQueryBuilder(!noQMarK, amp);
        for (var entry : queryParams) {
            if (entry.value() == null) {
                b.add(entry.name());
            } else {
                b.add(entry.name(), entry.value);
            }
        }
        return URI.create(template + b.build());
    }
}
