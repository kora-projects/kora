package ru.tinkoff.kora.s3.client.model.response;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record ListBucketResult(@Nullable List<String> commonPrefixes, int keyCount, @Nullable String nextContinuationToken, List<ListBucketItem> items) {

    public record ListBucketItem(
        String bucket, String key, String etag, String checksumType, String checksumAlgorithm, Instant lastModified, long size, @Nullable String storageClass, @Nullable ListBucketItemOwner owner
    ) {}

    public record ListBucketItemOwner(String displayName, String id) {}
}
