package ru.tinkoff.kora.aws.s3.model;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

public record ListMultipartUploadsResult(
    @Nullable String nextKeyMarker,
    @Nullable String nextUploadIdMarker,
    List<Upload> uploads) {

    public record Upload(String key, String uploadId, Instant initiated) {}

}
