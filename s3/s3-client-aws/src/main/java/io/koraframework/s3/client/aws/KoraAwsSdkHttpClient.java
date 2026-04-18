package io.koraframework.s3.client.aws;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.request.HttpClientRequestBuilder;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.body.HttpBodyOutput;
import software.amazon.awssdk.http.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KoraAwsSdkHttpClient implements SdkHttpClient {

    private final HttpClient httpClient;
    private final AwsS3Config clientConfig;

    public KoraAwsSdkHttpClient(HttpClient httpClient, AwsS3Config clientConfig) {
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
    }

    @Override
    public String clientName() {
        return "aws-kora";
    }

    @Override
    public ExecutableHttpRequest prepareRequest(HttpExecuteRequest httpExecuteRequest) {
        return new ExecutableHttpRequest() {

            @Override
            public HttpExecuteResponse call() {
                final HttpClientRequest request = asKoraRequest(httpExecuteRequest);
                final HttpClientResponse response = httpClient.execute(request);
                return asAwsResponse(response);
            }

            @Override
            public void abort() {
                // do nothing
            }
        };
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
