package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public class StreamingHttpBodyInput implements HttpBodyInput {
    @Nullable
    private final String contentType;
    private final long contentLength;
    private final InputStream content;

    public StreamingHttpBodyInput(@Nullable String contentType, long contentLength, InputStream content) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }

    @Override
    public long contentLength() {
        return this.contentLength;
    }

    @Nullable
    @Override
    public String contentType() {
        return this.contentType;
    }

    @Override
    public InputStream asInputStream() {
        return content;
    }
}
