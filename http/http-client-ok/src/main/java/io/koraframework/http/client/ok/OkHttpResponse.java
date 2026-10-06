package io.koraframework.http.client.ok;

import okhttp3.Response;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.EmptyHttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

public final class OkHttpResponse implements HttpClientResponse {

    private final Response response;

    public OkHttpResponse(Response response) {
        this.response = response;
    }

    @Override
    public int code() {
        return this.response.code();
    }

    @Override
    public HttpHeaders headers() {
        return new OkHttpHeaders(this.response.headers());
    }

    @Override
    public HttpBodyInput body() {
        // 304 never has a body, but may carry the Content-Length of the representation (RFC 9110 8.6):
        // reading OkHttp's body source would then wait for bytes that never come
        if (this.response.code() == 304) {
            return EmptyHttpBody.INSTANCE;
        }
        return new OkHttpResponseBody(this.response.body());
    }

    @Override
    public void close() {
        this.response.close();
    }

    @Override
    public String toString() {
        var body = body();
        return "HttpClientResponse{code=" + code() +
               ", bodyLength=" + body.contentLength() +
               ", bodyType=" + body.contentType() +
               ", headers=" + this.response.headers().size() +
               '}';
    }
}
