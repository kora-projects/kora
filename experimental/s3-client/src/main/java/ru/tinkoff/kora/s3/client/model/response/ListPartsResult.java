package ru.tinkoff.kora.s3.client.model.response;

import java.util.List;

public record ListPartsResult(Integer partNumberMarker, Integer nextPartNumberMarker, boolean truncated, List<UploadedPart> parts) {
}
