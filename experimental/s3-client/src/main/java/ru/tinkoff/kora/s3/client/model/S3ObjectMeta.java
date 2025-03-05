package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * S3 Object metadata representation
 */
@ApiStatus.Experimental
public record S3ObjectMeta(String bucket, String key, Instant modified, long size) {
    public S3ObjectMeta {
        Objects.requireNonNull(bucket);
        Objects.requireNonNull(key);
        Objects.requireNonNull(modified);
    }
}
