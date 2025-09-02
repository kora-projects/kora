package ru.tinkoff.kora.s3.client.aws;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

@ApiStatus.Experimental
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
                var contentProvider = httpExecuteRequest.contentStreamProvider().orElse(null);
                try (var content = contentProvider == null ? null : contentProvider.newStream()) {
                    final HttpClientRequest request = asKoraRequest(httpExecuteRequest, content);
                    final HttpClientResponse response = httpClient.execute(request);
                    return asAwsResponse(response);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
        final HttpClientResponse response;
        try {
            response = httpClient.execute(request);
        } catch (Exception e) {
            asyncExecuteRequest.responseHandler().onError(e);
            return CompletableFuture.completedFuture(null);
        }
        final SdkHttpResponse sdkHttpResponse = asSdkResponse(response);
        asyncExecuteRequest.responseHandler().onHeaders(sdkHttpResponse);
        try (var body = response.body(); var is = body.asInputStream()) {
            asyncExecuteRequest.responseHandler().onStream(AsyncRequestBody.fromBytes(is.readAllBytes()));
        } catch (IOException e) {
            asyncExecuteRequest.responseHandler().onError(e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private HttpClientRequest asKoraRequest(HttpExecuteRequest httpExecuteRequest, @Nullable InputStream content) {
        final SdkHttpRequest sdkHttpRequest = httpExecuteRequest.httpRequest();
        final HttpClientRequestBuilder builder = getBaseBuilder(sdkHttpRequest.getUri(), sdkHttpRequest.method().name(), sdkHttpRequest.rawQueryParameters(), sdkHttpRequest.headers());
        if (content != null) {
            var contentType = sdkHttpRequest.firstMatchingHeader("Content-Type").orElse("application/octet-stream");
            var contentLength = sdkHttpRequest.firstMatchingHeader("Content-Length").orElse(null);
            if (contentLength == null) {
                builder.body(HttpBodyOutput.of(contentType, content));
            } else {
                builder.body(HttpBodyOutput.of(contentType, Long.parseLong(contentLength), content));
            }
        }
        return builder
            .requestTimeout(clientConfig.requestTimeout())
            .build();
    }

    private HttpClientRequest asKoraRequest(AsyncExecuteRequest asyncExecuteRequest) {
        final SdkHttpRequest sdkHttpRequest = asyncExecuteRequest.request();
        final HttpClientRequestBuilder builder = getBaseBuilder(sdkHttpRequest.getUri(), sdkHttpRequest.method().name(), sdkHttpRequest.rawQueryParameters(), sdkHttpRequest.headers());

        Flow.Publisher<ByteBuffer> bodyFlow = JdkFlowAdapter.publisherToFlowPublisher(asyncExecuteRequest.requestContentPublisher());
        var contentType = sdkHttpRequest.firstMatchingHeader("Content-Type").orElse("application/octet-stream");
        var contentLengthStr = sdkHttpRequest.firstMatchingHeader("Content-Length").orElse(null);
        var contentLength = contentLengthStr == null ? -1 : Long.parseLong(contentLengthStr);
        builder.body(new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return contentLength;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public void write(OutputStream os) throws IOException {
                var future = new CompletableFuture<Void>();
                asyncExecuteRequest.requestContentPublisher().subscribe(new Subscriber<ByteBuffer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer byteBuffer) {
                        try {
                            os.write(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onComplete() {
                        future.complete(null);
                    }
                });
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() throws IOException {

            }
        });
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

        return AbortableInputStream.create(bodyIS, () -> {
            try {
                bodyIS.close();
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
