package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class KoraAwsSdkHttpClient implements SdkHttpClient, SdkAsyncHttpClient {

    private final HttpClient httpClient;

    public KoraAwsSdkHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
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
                final HttpClientRequest request = asKoraRequest(httpExecuteRequest.httpRequest());
                final HttpClientResponse response = httpClient.execute(request).toCompletableFuture().join();
                final SdkHttpResponse sdkHttpResponse = asSdkResponse(response);
                return HttpExecuteResponse.builder()
                    .response(sdkHttpResponse)
                    .build();
            }

            @Override
            public void abort() {

            }
        };
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncExecuteRequest) {
        final HttpClientRequest request = asKoraRequest(asyncExecuteRequest.request());
        return httpClient.execute(request)
            .thenAccept(response -> {
                final SdkHttpResponse sdkHttpResponse = asSdkResponse(response);
                asyncExecuteRequest.responseHandler().onHeaders(sdkHttpResponse);
            })
            .exceptionally(e -> {
                asyncExecuteRequest.responseHandler().onError(e);
                return null;
            })
            .toCompletableFuture();
    }

    private static HttpClientRequest asKoraRequest(SdkHttpRequest sdkHttpRequest) {
        final HttpClientRequestBuilder builder = HttpClientRequest.of(sdkHttpRequest.method().name(), sdkHttpRequest.getUri().toString());
        sdkHttpRequest.headers().forEach(builder::header);
        sdkHttpRequest.rawQueryParameters().forEach(builder::queryParam);
        return builder.build();
    }

    private static SdkHttpResponse asSdkResponse(HttpClientResponse koraHttpResponse) {
        final SdkHttpFullResponse.Builder sdkResponseBuilder = SdkHttpResponse.builder();
        final Map<String, List<String>> responseHeaders = new HashMap<>();
        koraHttpResponse.headers().forEach(e -> responseHeaders.put(e.getKey(), e.getValue()));
        sdkResponseBuilder.headers(responseHeaders);

        final HttpBodyInput body = koraHttpResponse.body();
        final InputStream bodyAsInputStream = body.asInputStream() != null
            ? body.asInputStream()
            : new ByteArrayInputStream(body.asArrayStage().toCompletableFuture().join());

        sdkResponseBuilder.content(AbortableInputStream.create(bodyAsInputStream, () -> {
            try {
                bodyAsInputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }));

        return sdkResponseBuilder.build();
    }

    @Override
    public void close() {

    }
}
