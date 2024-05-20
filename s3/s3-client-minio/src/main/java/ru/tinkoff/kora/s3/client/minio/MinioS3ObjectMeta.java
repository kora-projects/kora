package ru.tinkoff.kora.s3.client.minio;

import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;

import java.time.Instant;
import java.util.Objects;

final class MinioS3ObjectMeta implements S3ObjectMeta {

    private final String key;
    private final Instant modified;
    private final long size;

    public MinioS3ObjectMeta(Item item) {
        this.key = item.objectName();
        this.modified = item.lastModified().toInstant();
        this.size = item.size();
    }

    public MinioS3ObjectMeta(StatObjectResponse response) {
        this.key = response.object();
        this.modified = response.lastModified().toInstant();
        this.size = response.size();
        response.contentType();
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
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MinioS3ObjectMeta that = (MinioS3ObjectMeta) object;
        return size == that.size && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, size);
    }

    @Override
    public String toString() {
        return "MinioS3ObjectMeta{key=" + key +
            ", modified=" + modified +
            ", size=" + size +
            '}';
    }
}
