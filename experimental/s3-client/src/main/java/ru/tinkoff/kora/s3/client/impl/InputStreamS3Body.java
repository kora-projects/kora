package ru.tinkoff.kora.s3.client.impl;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.model.S3Body;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@ApiStatus.Experimental
public final class InputStreamS3Body implements S3Body {
    private final InputStream inputStream;
    private final long size;
    @Nullable
    private final String contentType;
    @Nullable
    private final String encoding;

    public InputStreamS3Body(InputStream inputStream, long size, @Nullable String contentType, @Nullable String encoding) {
        this.inputStream = Objects.requireNonNull(inputStream);
        this.size = size;
        this.contentType = contentType;
        this.encoding = encoding;
    }

    @Override
    public InputStream asInputStream() {
        return inputStream;
    }

    @Override
    public void close() throws IOException {
        this.inputStream.close();
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    @Nullable
    public String contentType() {
        return contentType;
    }

    @Override
    @Nullable
    public String encoding() {
        return encoding;
    }

    @Override
    public String toString() {
        return "InputStreamS3Body[" +
            "size=" + size + ", " +
            "contentType=" + contentType + ", " +
            "encoding=" + encoding + ']';
    }

}
