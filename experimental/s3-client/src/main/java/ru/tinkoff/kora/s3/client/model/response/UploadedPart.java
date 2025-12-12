package ru.tinkoff.kora.s3.client.model.response;

import jakarta.annotation.Nullable;

public record UploadedPart(
    @Nullable
    String checksumCRC32,
    @Nullable
    String checksumCRC32C,
    @Nullable
    String checksumCRC64NVME,
    @Nullable
    String checksumSHA1,
    @Nullable
    String checksumSHA256,
    String eTag,
    int partNumber,
    long size
) {
}
