package ru.tinkoff.kora.http.client.ok;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.internal.http.HttpMethod;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.*;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.io.IOException;

public final class OkHttpClient implements HttpClient {
    private final okhttp3.OkHttpClient client;

    public OkHttpClient(okhttp3.OkHttpClient client) {
        this.client = client;
    }

    @Override
    public HttpClientResponse execute(HttpClientRequest request) {
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
            return new OkHttpResponse(rs);
        } catch (HttpClientException e) {
            throw e;
        } catch (java.io.InterruptedIOException t) {
            if ("timeout".equals(t.getMessage())) {
                throw new HttpClientTimeoutException(t);
            } else {
                throw new HttpClientConnectionException(t);
            }
        } catch (IOException t) {
            throw new HttpClientConnectionException(t);
        } catch (Throwable t) {
            throw new HttpClientUnknownException(t);
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
