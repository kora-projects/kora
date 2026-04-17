package io.koraframework.s3.client.kora.model.response;

import java.util.List;

public record ListPartsResult(Integer partNumberMarker,
                              Integer nextPartNumberMarker,
                              boolean truncated,
                              List<UploadedPart> parts) {
}
