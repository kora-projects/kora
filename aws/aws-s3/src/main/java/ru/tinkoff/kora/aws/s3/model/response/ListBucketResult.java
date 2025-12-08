package ru.tinkoff.kora.aws.s3.model.response;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

public record ListBucketResult(@Nullable List<String> commonPrefixes, int keyCount, @Nullable String nextContinuationToken, List<ListBucketItem> items) {

    public record ListBucketItem(
        String bucket, String key, String etag, String checksumType, String checksumAlgorithm, Instant lastModified, long size, @Nullable String storageClass, @Nullable ListBucketItemOwner owner
    ) {}

    public record ListBucketItemOwner(String displayName, String id) {}
}
