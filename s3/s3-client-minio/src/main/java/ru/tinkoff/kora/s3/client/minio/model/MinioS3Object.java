package ru.tinkoff.kora.s3.client.minio.model;

import io.minio.GetObjectResponse;
import okhttp3.Headers;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3Object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;

public class MinioS3Object implements S3Object {

    private final S3Body body;
    private final String key;
    private final Instant modified;
    private final long size;

    public MinioS3Object(GetObjectResponse response) {
        this.key = response.object();
        this.modified = response.headers().getInstant("Modified");
        try {
            final Headers headers = response.headers();
            this.size = headers.get("Content-Length") == null
                ? response.available()
                : Long.valueOf(headers.get("Content-Length"));
            this.body = new MinioS3Body(response, size, headers.get("Content-Encoding"), headers.get("Content-Type"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Instant modified() {
        return modified;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public S3Body body() {
        return body;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MinioS3Object that = (MinioS3Object) object;
        return size == that.size && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, size);
    }

    @Override
    public String toString() {
        return "MinioS3Object{key=" + key +
            ", size=" + size +
            ", modified=" + modified +
            '}';
    }
}
