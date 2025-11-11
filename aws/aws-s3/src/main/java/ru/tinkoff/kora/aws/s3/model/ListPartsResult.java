package ru.tinkoff.kora.aws.s3.model;

import java.util.List;

public record ListPartsResult(Integer partNumberMarker, Integer nextPartNumberMarker, boolean truncated, List<UploadedPart> parts) {
}
