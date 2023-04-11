package ru.tinkoff.kora.http.client.common.request;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpClientRequestBuilder {
    private String method;
    private String uriTemplate;
    private List<HttpClientRequest.TemplateParam> templateParams = new ArrayList<>();
    private List<HttpClientRequest.QueryParam> queryParams = new ArrayList<>();
    private HashMap<String, List<String>> headers = new HashMap<>();
    private Flux<ByteBuffer> body = Flux.empty();
    private int requestTimeout = -1;

    public HttpClientRequestBuilder(String method, String uriTemplate) {
        this.method = method;
        this.uriTemplate = uriTemplate;
    }

    public HttpClientRequestBuilder(HttpClientRequest httpClientRequest) {
        this.method = httpClientRequest.method();
        this.uriTemplate = httpClientRequest.uriTemplate();
        this.templateParams = httpClientRequest.templateParams();
        this.queryParams = httpClientRequest.queryParams();
        this.headers = fromHeaders(httpClientRequest.headers());
        this.body = httpClientRequest.body();
        this.requestTimeout = httpClientRequest.requestTimeout();
    }


    public HttpClientRequestBuilder uriTemplate(String uriTemplate) {
        this.uriTemplate = uriTemplate;

        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, String value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, value));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, int value) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, Integer.toString(value)));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, long value) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, Long.toString(value)));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, boolean value) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, Boolean.toString(value)));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, String value) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, Objects.requireNonNull(value)));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, UUID value) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, value.toString()));

        return this;
    }

    public HttpClientRequestBuilder queryParam(String name, Collection<String> value) {
        if (value.isEmpty()) {
            return this.queryParam(name);
        }
        for (var val : value) {
            this.queryParams.add(new HttpClientRequest.QueryParam(name, val));
        }
        return this;
    }

    public HttpClientRequestBuilder queryParam(String name) {
        this.queryParams.add(new HttpClientRequest.QueryParam(name, null));
        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, List<String> value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, String.join(",", value)));

        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, Integer value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, value.toString()));

        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, Long value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, value.toString()));

        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, Boolean value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, value.toString()));

        return this;
    }

    public HttpClientRequestBuilder templateParam(String name, UUID value) {
        this.templateParams.add(new HttpClientRequest.TemplateParam(name, value.toString()));

        return this;
    }

    public HttpClientRequestBuilder header(String name, String value) {
        var headers = this.headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>(1));
        headers.add(value);

        return this;
    }

    public HttpClientRequestBuilder requestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;

        return this;
    }

    public HttpClientRequestBuilder body(Flux<ByteBuffer> body) {
        this.body = body;

        return this;
    }

    public HttpClientRequestBuilder body(ByteBuffer body) {
        this.body = Flux.just(body);

        return this;
    }

    public HttpClientRequestBuilder body(byte[] body) {
        this.body = Flux.just(ByteBuffer.wrap(body));

        return this;
    }

    public HttpClientRequestBuilder headers(HttpHeaders headers) {
        this.headers = fromHeaders(headers);

        return this;
    }

    public HttpClientRequest build() {
        var resolvedUri = resolveUri(this.uriTemplate, this.templateParams, this.queryParams);
        var uri = URI.create(resolvedUri);
        var authority = uri.getAuthority();
        var operation = operation(this.method, this.uriTemplate, uri);

        return new HttpClientRequest.Default(
            this.method, this.uriTemplate, this.queryParams, this.templateParams, toHeaders(this.headers), this.body, this.requestTimeout, resolvedUri, authority, operation
        );
    }

    private static String resolveUri(String uriTemplate, List<HttpClientRequest.TemplateParam> templateParams, List<HttpClientRequest.QueryParam> queryParams) {
        var template = uriTemplate;
        if (!templateParams.isEmpty()) {
            for (var i = templateParams.listIterator(templateParams.size()); i.hasPrevious(); ) {
                var entry = i.previous();
                template = template.replace("{" + entry.name() + "}", URLEncoder.encode(entry.value(), UTF_8));
            }
        }
        if (queryParams.isEmpty()) {
            return template;
        }
        var delimeter = template.contains("?")
            ? (template.endsWith("?") ? "" : "&")
            : "?";
        var sb = new StringBuilder(template)
            .append(delimeter);
        var firstEntry = queryParams.get(0);
        sb.append(URLEncoder.encode(firstEntry.name(), UTF_8));
        if (firstEntry.value() != null) {
            sb.append('=').append(URLEncoder.encode(firstEntry.value(), UTF_8));
        }
        for (var i = 1; i < queryParams.size(); i++) {
            var entry = queryParams.get(i);
            sb.append('&').append(URLEncoder.encode(entry.name(), UTF_8));
            if (entry.value() != null) {
                sb.append('=').append(URLEncoder.encode(entry.value(), UTF_8));
            }
        }
        return sb.toString();
    }

    private static String operation(String method, String uriTemplate, URI uri) {
        if (uri.getAuthority() != null) {
            if (uri.getScheme() != null) {
                uriTemplate = uriTemplate.replace(uri.getScheme() + "://" + uri.getAuthority(), "");
            }
        }
        var questionMark = uriTemplate.indexOf('?');
        if (questionMark >= 0) {
            uriTemplate = uriTemplate.substring(0, questionMark);
        }
        return method + " " + uriTemplate;
    }

    private static HashMap<String, List<String>> fromHeaders(HttpHeaders headers) {
        var collect = new HashMap<String, List<String>>(headers.size());
        for (var e : headers) {
            collect.putIfAbsent(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return collect;
    }

    @SuppressWarnings("unchecked")
    private static HttpHeaders toHeaders(Map<String, List<String>> map) {
        return HttpHeaders.of(map.entrySet().toArray(Map.Entry[]::new));
    }

}
