package ru.tinkoff.kora.s3.client.aws;

import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public final class KoraAwsSdkHttpClient implements SdkHttpClient, SdkAsyncHttpClient {

    private final HttpClient httpClient;
    private final AwsS3ClientConfig clientConfig;

    public KoraAwsSdkHttpClient(HttpClient httpClient, AwsS3ClientConfig clientConfig) {
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
    }

    @Override
    public String clientName() {
        return "Kora";
    }

    @Override
    public ExecutableHttpRequest prepareRequest(HttpExecuteRequest httpExecuteRequest) {
        return new ExecutableHttpRequest() {

            @Override
            public HttpExecuteResponse call() {
                final HttpClientRequest request = asKoraRequest(httpExecuteRequest);
                final HttpClientResponse response = httpClient.execute(request).toCompletableFuture().join();
                return asAwsResponse(response);
            }

            @Override
            public void abort() {
                // do nothing
            }
        };
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncExecuteRequest) {
        final HttpClientRequest request = asKoraRequest(asyncExecuteRequest);
        return httpClient.execute(request)
            .thenAccept(response -> {
                final SdkHttpResponse sdkHttpResponse = asSdkResponse(response);
                asyncExecuteRequest.responseHandler().onHeaders(sdkHttpResponse);
                asyncExecuteRequest.responseHandler().onStream(JdkFlowAdapter.flowPublisherToFlux(response.body()));
            })
            .exceptionally(e -> {
                asyncExecuteRequest.responseHandler().onError(e);
                return null;
            })
            .toCompletableFuture();
    }

    private HttpClientRequest asKoraRequest(HttpExecuteRequest httpExecuteRequest) {
        final SdkHttpRequest sdkHttpRequest = httpExecuteRequest.httpRequest();
        final HttpClientRequestBuilder builder = getBaseBuilder(sdkHttpRequest.getUri(), sdkHttpRequest.method().name(), sdkHttpRequest.rawQueryParameters(), sdkHttpRequest.headers());

        httpExecuteRequest.contentStreamProvider().ifPresent(provider -> {
            String contentType = sdkHttpRequest.firstMatchingHeader("Content-Type").orElse("application/octet-stream");
            String contentLength = sdkHttpRequest.firstMatchingHeader("Content-Length").orElse(null);
            if (contentLength == null) {
                builder.body(HttpBodyOutput.of(contentType, provider.newStream()));
            } else {
                builder.body(HttpBodyOutput.of(contentType, Long.parseLong(contentLength), provider.newStream()));
            }
        });

        return builder
            .requestTimeout(clientConfig.requestTimeout())
            .build();
    }

    private HttpClientRequest asKoraRequest(AsyncExecuteRequest asyncExecuteRequest) {
        final SdkHttpRequest sdkHttpRequest = asyncExecuteRequest.request();
        final HttpClientRequestBuilder builder = getBaseBuilder(sdkHttpRequest.getUri(), sdkHttpRequest.method().name(), sdkHttpRequest.rawQueryParameters(), sdkHttpRequest.headers());

        Flow.Publisher<ByteBuffer> bodyFlow = JdkFlowAdapter.publisherToFlowPublisher(asyncExecuteRequest.requestContentPublisher());
        String contentType = sdkHttpRequest.firstMatchingHeader("Content-Type").orElse("application/octet-stream");
        String contentLength = sdkHttpRequest.firstMatchingHeader("Content-Length").orElse(null);
        if (contentLength == null) {
            builder.body(HttpBodyOutput.of(contentType, bodyFlow));
        } else {
            builder.body(HttpBodyOutput.of(contentType, Long.parseLong(contentLength), bodyFlow));
        }

        return builder
            .requestTimeout(clientConfig.requestTimeout())
            .build();
    }

    private static HttpClientRequestBuilder getBaseBuilder(URI sdkUri,
                                                           String method,
                                                           Map<String, List<String>> rawQueryParameters,
                                                           Map<String, List<String>> headers) {
        try {
            final URI uri = new URI(sdkUri.getScheme(),
                sdkUri.getAuthority(),
                sdkUri.getPath(),
                null, // Ignore the query part of the input url
                sdkUri.getFragment());

            final HttpClientRequestBuilder builder = HttpClientRequest.of(method, uri.toString());
            rawQueryParameters.forEach((k, v) -> {
                if (v == null || v.isEmpty() || v.get(0) == null) {
                    builder.queryParam(k);
                } else {
                    builder.queryParam(k, v);
                }
            });

            headers.forEach((k, v) -> {
                if (!"host".equalsIgnoreCase(k) && !"expect".equalsIgnoreCase(k)) {
                    builder.header(k, v);
                }
            });

            return builder;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static HttpExecuteResponse asAwsResponse(HttpClientResponse koraHttpResponse) {
        final SdkHttpFullResponse.Builder sdkResponseBuilder = SdkHttpResponse.builder();
        final Map<String, List<String>> responseHeaders = new HashMap<>();
        koraHttpResponse.headers().forEach(e -> responseHeaders.put(e.getKey(), e.getValue()));
        sdkResponseBuilder.headers(responseHeaders);
        sdkResponseBuilder.statusCode(koraHttpResponse.code());
        sdkResponseBuilder.statusText(String.valueOf(koraHttpResponse.code()));

        AbortableInputStream bodyStream = asSdkResponseStream(koraHttpResponse);
        sdkResponseBuilder.content(bodyStream);

        final SdkHttpFullResponse sdkHttpResponse = sdkResponseBuilder.build();
        return HttpExecuteResponse.builder()
            .response(sdkHttpResponse)
            .responseBody(bodyStream)
            .build();
    }

    private static SdkHttpFullResponse asSdkResponse(HttpClientResponse koraHttpResponse) {
        final SdkHttpFullResponse.Builder sdkResponseBuilder = SdkHttpResponse.builder();
        final Map<String, List<String>> responseHeaders = new HashMap<>();
        koraHttpResponse.headers().forEach(e -> responseHeaders.put(e.getKey(), e.getValue()));
        sdkResponseBuilder.headers(responseHeaders);
        sdkResponseBuilder.statusCode(koraHttpResponse.code());
        sdkResponseBuilder.statusText(String.valueOf(koraHttpResponse.code()));

        return sdkResponseBuilder.build();
    }

    private static AbortableInputStream asSdkResponseStream(HttpClientResponse koraHttpResponse) {
        final HttpBodyInput body = koraHttpResponse.body();
        final InputStream bodyIS = body.asInputStream();
        final InputStream bodyAsInputStream = bodyIS != null
            ? bodyIS
            : new ByteArrayInputStream(body.asArrayStage().toCompletableFuture().join());

        return AbortableInputStream.create(bodyAsInputStream, () -> {
            try {
                bodyAsInputStream.close();
            } catch (IOException e) {
                // ignore
            }
        });
    }

    @Override
    public void close() {
        // do nothing
    }
}
