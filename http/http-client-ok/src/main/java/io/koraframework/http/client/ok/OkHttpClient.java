package io.koraframework.http.client.ok;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.exception.HttpClientConnectionException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.exception.HttpClientUnknownException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class OkHttpClient implements HttpClient {

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
        if (!permitsRequestBody(request.method())) {
            if (body != null) {
                body.close();
            }
            return null;
        }
        if (body == null && requiresRequestBody(request.method())) {
            return RequestBody.create(new byte[0]);
        }
        return new OkHttpRequestBody(body);
    }

    // Can't use direct HttpMethod.permitsRequestBody cause its internal and can't be exposed via module-info.java
    private static boolean permitsRequestBody(String method) {
        return !method.equals("GET") && !method.equals("HEAD");
    }

    // Can't use direct HttpMethod.requiresRequestBody cause its internal and can't be exposed via module-info.java
    private static boolean requiresRequestBody(String method) {
        return method.equals("POST")
            || method.equals("PUT")
            || method.equals("PATCH")
            || method.equals("PROPPATCH")
            || method.equals("QUERY")
            || method.equals("REPORT"); // WebDAV
    }
}
