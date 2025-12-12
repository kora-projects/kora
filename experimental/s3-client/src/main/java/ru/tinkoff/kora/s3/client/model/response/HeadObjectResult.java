package ru.tinkoff.kora.s3.client.model.response;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public record HeadObjectResult(String bucket, String key, long size, HttpHeaders headers) {
    public String etag() {
        return headers.getFirst("ETag");
    }

    public String versionId() {
        return headers.getFirst("x-amz-version-id");
    }

    @Nullable
    public Instant lastModified() {
        var lastModified = headers.getFirst("Last-Modified");
        if (lastModified == null) {
            return null;
        }
        return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lastModified));
    }
}
