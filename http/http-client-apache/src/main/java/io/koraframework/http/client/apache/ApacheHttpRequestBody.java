package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.exception.HttpClientConnectionException;
import io.koraframework.http.client.common.exception.HttpClientEncoderException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBodyOutput;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Set;

public class ApacheHttpRequestBody implements HttpEntity {

    private final HttpBodyOutput body;
    @Nullable
    private final String contentEncoding;

    public ApacheHttpRequestBody(HttpClientRequest request) {
        this(request.body(), request.headers().getFirst("content-encoding"));
    }

    public ApacheHttpRequestBody(HttpBodyOutput body, @Nullable String contentEncoding) {
        this.body = body;
        this.contentEncoding = contentEncoding;
    }

    @Override
    public boolean isRepeatable() {
        // a full body can be resent, e.g. on a 307/308 redirect
        return this.body.getFullContentIfAvailable() != null;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        var full = this.body.getFullContentIfAvailable();
        if (full != null) {
            if (full.hasArray()) {
                return new ByteArrayInputStream(full.array(), full.arrayOffset() + full.position(), full.remaining());
            }
            var bytes = new byte[full.remaining()];
            full.duplicate().get(bytes);
            return new ByteArrayInputStream(bytes);
        }
        var baos = new ByteArrayOutputStream();
        this.body.write(baos);
        return new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Override
    public void writeTo(OutputStream outStream) {
        try {
            var full = this.body.getFullContentIfAvailable();
            if (full != null) {
                Channels.newChannel(outStream).write(full.duplicate());
                return;
            }
            this.body.write(outStream);
        } catch (IOException e) {
            throw new HttpClientConnectionException(e);
        } catch (Exception e) {
            throw new HttpClientEncoderException(e);
        }
    }

    @Override
    public boolean isStreaming() {
        return this.body.getFullContentIfAvailable() == null;
    }

    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return null;
    }

    @Override
    public void close() throws IOException {
        this.body.close();
    }

    @Override
    public long getContentLength() {
        return this.body.contentLength();
    }

    @Override
    public String getContentType() {
        return this.body.contentType();
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Set.of();
    }

    @Override
    public String toString() {
        return body.toString();
    }
}
