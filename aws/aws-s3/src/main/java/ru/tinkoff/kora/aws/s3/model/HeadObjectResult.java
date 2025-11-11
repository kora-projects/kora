package ru.tinkoff.kora.aws.s3.model;

import java.time.Instant;

public record HeadObjectResult(String bucket, String key, String etag, long size, String versionId, Instant lastModified) {

}
