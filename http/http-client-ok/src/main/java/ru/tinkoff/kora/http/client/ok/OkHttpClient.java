package ru.tinkoff.kora.http.client.ok;

import jakarta.annotation.Nullable;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.internal.http.HttpMethod;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConnectionException;
import ru.tinkoff.kora.http.client.common.HttpClientTimeoutException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class OkHttpClient implements HttpClient {
    private final okhttp3.OkHttpClient client;

    public OkHttpClient(okhttp3.OkHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<HttpClientResponse> execute(HttpClientRequest request) {
        try {
            var b = new Request.Builder();
            b.method(request.method(), toRequestBody(request))
                .url(request.uri().toURL());
            for (var header : request.headers()) {
                for (var headerValue : header.getValue()) {
                    b.addHeader(header.getKey(), headerValue);
                }
            }
            var okHttpRequest = b.build();
            var okHttpClient = this.client;
            if (request.requestTimeout() != null) {
                okHttpClient = okHttpClient.newBuilder().callTimeout(request.requestTimeout()).build();
            }
            var call = okHttpClient.newCall(okHttpRequest);
            var rs = call.execute();
            return CompletableFuture.completedFuture(new OkHttpResponse(rs));
        } catch (java.io.InterruptedIOException t) {
            if ("timeout".equals(t.getMessage())) {
                return CompletableFuture.failedFuture(new HttpClientTimeoutException(t));
            } else {
                return CompletableFuture.failedFuture(new HttpClientConnectionException(t));
            }
        } catch (IOException t) {
            return CompletableFuture.failedFuture(new HttpClientConnectionException(t));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    @Nullable
    private RequestBody toRequestBody(HttpClientRequest request) throws IOException {
        var body = request.body();
        if (!HttpMethod.permitsRequestBody(request.method())) {
            if (body != null) {
                body.close();
            }
            return null;
        }
        if (body == null && HttpMethod.requiresRequestBody(request.method())) {
            return RequestBody.create(new byte[0]);
        }
        return new OkHttpRequestBody(body);
    }
}
