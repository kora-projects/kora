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
import java.util.List;
import java.util.Set;

public class ApacheHttpRequestBody implements HttpEntity {

    private final HttpBodyOutput body;
    private final String contentEncoding;

    public ApacheHttpRequestBody(HttpClientRequest request) {
        this(request.body(), request.headers().getFirst("content-encoding"));
    }

    public ApacheHttpRequestBody(HttpBodyOutput body, @Nullable String contentEncoding) {
        this.body = body;
        if (contentEncoding != null) {
            this.contentEncoding = contentEncoding;
        } else {
            var type = body.contentType();
            if (type != null) {
                var encoding = type.split(";");
                if (encoding.length > 1) {
                    this.contentEncoding = encoding[1].strip();
                } else {
                    this.contentEncoding = null;
                }
            } else {
                this.contentEncoding = null;
            }
        }
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        var baos = new ByteArrayOutputStream();
        this.body.write(baos);
        return new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Override
    public void writeTo(OutputStream outStream) {
        try {
            this.body.write(outStream);
        } catch (IOException e) {
            throw new HttpClientConnectionException(e);
        } catch (Exception e) {
            throw new HttpClientEncoderException(e);
        }
    }

    @Override
    public boolean isStreaming() {
        return true;
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
