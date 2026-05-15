package io.koraframework.http.client.apache;

import io.koraframework.http.common.body.HttpBodyInput;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class ApacheHttpResponseBody implements HttpBodyInput {

    private final HttpEntity httpEntity;

    public ApacheHttpResponseBody(HttpEntity httpEntity) {
        this.httpEntity = httpEntity;
    }

    @Override
    public InputStream asInputStream() {
        try {
            var result = httpEntity.getContent();
            if (result == null) {
                return InputStream.nullInputStream();
            } else {
                return result;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get response body as input stream", e);
        }
    }

    @Nullable
    @Override
    public ByteBuffer getFullContentIfAvailable() {
        try {
            return switch (httpEntity) {
                case ByteArrayEntity bae -> ByteBuffer.wrap(bae.getContent().readAllBytes());
                case BufferedHttpEntity bhe -> ByteBuffer.wrap(bhe.getContent().readAllBytes());
                case StringEntity se -> ByteBuffer.wrap(se.getContent().readAllBytes());
                case null, default -> null;
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long contentLength() {
        return httpEntity.getContentLength();
    }

    @Override
    public String contentType() {
        return httpEntity.getContentType();
    }

    @Override
    public void close() throws IOException {
        httpEntity.close();
    }
}
