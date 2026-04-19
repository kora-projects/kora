package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBodyOutput;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class DefaultHttpClientTelemetryRequestBodyWrapper implements HttpBodyOutput {

    private final HttpClientRequest request;
    private final HttpBodyOutput body;
    private final DefaultHttpClientLogger logger;

    public DefaultHttpClientTelemetryRequestBodyWrapper(HttpClientRequest request,
                                                        HttpBodyOutput body,
                                                        DefaultHttpClientLogger logger) {
        this.request = request;
        this.body = body;
        this.logger = logger;
    }

    @Override
    public long contentLength() {
        return body.contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        return body.contentType();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        var baos = new ByteArrayOutputStream();
        body.write(baos);
        ByteBuffer bodyBuffer = null;
        if (logger.logRequestBody()) {
            var array = baos.toByteArray();
            if (array.length > 0) {
                bodyBuffer = ByteBuffer.wrap(baos.toByteArray());
            }
        }

        baos.writeTo(os);
        if (logger.logRequestBody() && bodyBuffer != null) {
            logger.logRequest(request, bodyBuffer, contentType());
        } else {
            logger.logRequest(request, null, contentType());
        }
    }

    @Override
    public void close() throws IOException {
        body.close();
    }

    @Override
    public String toString() {
        return "HttpBodyOutputWrapper{contentLength=" + contentLength()
               + ", contentType=" + contentType()
               + '}';
    }
}
