package ru.tinkoff.kora.http.common.body;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputStreamHttpBodyOutput implements HttpBodyOutput {
    @Nullable
    private final String contentType;
    private final long contentLength;
    private final InputStream content;

    public InputStreamHttpBodyOutput(@Nullable String contentType, long contentLength, InputStream content) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
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
    public void write(OutputStream os) throws IOException {
        content.transferTo(os);
    }

    @Override
    public void close() throws IOException {
        this.content.close();
    }
}
