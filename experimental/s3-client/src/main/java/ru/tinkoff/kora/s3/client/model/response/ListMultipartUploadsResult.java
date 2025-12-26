package ru.tinkoff.kora.s3.client.model.response;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record ListMultipartUploadsResult(
    @Nullable String nextKeyMarker,
    @Nullable String nextUploadIdMarker,
    List<Upload> uploads) {

    public record Upload(String key, String uploadId, Instant initiated) {}

}
