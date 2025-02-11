package ru.tinkoff.kora.s3.client.model;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.impl.ByteArrayS3Body;
import ru.tinkoff.kora.s3.client.impl.InputStreamS3Body;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * S3 Object value representation
 */
@ApiStatus.Experimental
public sealed interface S3Body extends Closeable permits ByteArrayS3Body, InputStreamS3Body {

    default byte[] asBytes() throws IOException {
        try (var stream = asInputStream()) {
            return stream.readAllBytes();
        }
    }

    InputStream asInputStream();

    long size();

    @Nullable
    String encoding();

    @Nullable
    String contentType();

    static S3Body ofBytes(byte[] body) {
        return new ByteArrayS3Body(body, 0, body.length, "application/octet-stream", null);
    }

    static S3Body ofBytes(byte[] body, @Nullable String type) {
        return new ByteArrayS3Body(body, 0, body.length, type, null);
    }

    static S3Body ofBytes(byte[] body, @Nullable String type, @Nullable String encoding) {
        return new ByteArrayS3Body(body, 0, body.length, type, encoding);
    }

    static S3Body ofBytes(byte[] body, int offset, int length) {
        return new ByteArrayS3Body(body, offset, length, "application/octet-stream", null);
    }

    static S3Body ofBytes(byte[] body, int offset, int length, @Nullable String type) {
        return new ByteArrayS3Body(body, offset, length, type, null);
    }

    static S3Body ofBytes(byte[] body, int offset, int length, @Nullable String type, @Nullable String encoding) {
        return new ByteArrayS3Body(body, offset, length, type, encoding);
    }

    static S3Body ofInputStream(InputStream inputStream, long size) {
        return new InputStreamS3Body(inputStream, size, null, null);
    }

    static S3Body ofInputStream(InputStream inputStream, long size, @Nullable String type) {
        return new InputStreamS3Body(inputStream, size, type, null);
    }

    static S3Body ofInputStream(InputStream inputStream, long size, @Nullable String type, @Nullable String encoding) {
        return new InputStreamS3Body(inputStream, size, type, encoding);
    }

}
