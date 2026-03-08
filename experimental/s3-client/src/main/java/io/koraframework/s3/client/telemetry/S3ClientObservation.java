package io.koraframework.s3.client.telemetry;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.telemetry.Observation;

public interface S3ClientObservation extends Observation {
    void observeKey(String key);

    void observeUploadId(String uploadId);

    void observeAwsRequestId(@Nullable String amxRequestId);

    void observeAwsExtendedId(@Nullable String amxRequestId);
}
