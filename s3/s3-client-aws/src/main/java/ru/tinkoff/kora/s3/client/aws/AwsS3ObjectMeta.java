package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.Objects;

final class AwsS3ObjectMeta implements S3ObjectMeta {

    private final String key;
    private final Instant modified;
    private final long size;

    public AwsS3ObjectMeta(String key, GetObjectResponse response) {
        this.key = key;
        this.modified = response.lastModified();
        this.size = response.contentLength();
    }

    public AwsS3ObjectMeta(String key, GetObjectAttributesResponse response) {
        this.key = key;
        this.modified = response.lastModified();
        this.size = response.objectSize();
    }

    public AwsS3ObjectMeta(S3Object object) {
        this.key = object.key();
        this.modified = object.lastModified();
        this.size = object.size();
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
        AwsS3ObjectMeta meta = (AwsS3ObjectMeta) object;
        return size == meta.size && Objects.equals(key, meta.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, size);
    }

    @Override
    public String toString() {
        return "AwsS3ObjectMeta{key=" + key +
            ", size=" + size +
            ", modified=" + modified +
            '}';
    }
}
