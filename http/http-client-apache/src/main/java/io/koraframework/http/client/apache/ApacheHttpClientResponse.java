package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.EmptyHttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApacheHttpClientResponse implements HttpClientResponse {

    private final ClassicHttpResponse response;
    private final HttpBodyInput bodyInput;

    public ApacheHttpClientResponse(ClassicHttpResponse response) {
        this.response = response;
        this.bodyInput = (response.getEntity() == null)
            ? EmptyHttpBody.INSTANCE
            : new ApacheHttpResponseBody(response.getEntity());
    }

    @Override
    public int code() {
        return this.response.getCode();
    }

    @Override
    public HttpHeaders headers() {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();

        for (Header header : this.response.getHeaders()) {
            headerMap.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }

        return HttpHeaders.of(headerMap);
    }

    @Override
    public HttpBodyInput body() {
        return this.bodyInput;
    }

    @Override
    public void close() throws IOException {
        this.response.close();
    }

    @Override
    public String toString() {
        long contentLength = response.getEntity() != null ? response.getEntity().getContentLength() : -1;
        String contentType = response.getEntity() != null ? response.getEntity().getContentType() : null;

        return "HttpClientResponse{code=" + code() +
               ", bodyLength=" + contentLength +
               ", bodyType=" + contentType +
               '}';
    }
}
