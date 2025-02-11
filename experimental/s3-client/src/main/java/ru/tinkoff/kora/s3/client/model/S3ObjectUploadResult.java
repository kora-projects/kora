package ru.tinkoff.kora.s3.client.model;

public record S3ObjectUploadResult(String bucket, String key, String etag, String versionId) {
    public S3ObjectUploadResult {
    }
}
