package ru.tinkoff.kora.aws.s3.model.response;

import java.time.Instant;

public record HeadObjectResult(String bucket, String key, String etag, long size, String versionId, Instant lastModified) {

}
