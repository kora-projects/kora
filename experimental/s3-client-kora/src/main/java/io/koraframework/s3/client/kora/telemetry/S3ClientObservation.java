package io.koraframework.s3.client.kora.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.jspecify.annotations.Nullable;

public interface S3ClientObservation extends Observation {

    void observeKey(String key);

    void observeUploadId(String uploadId);

    void observeAwsRequestId(@Nullable String amxRequestId);

    void observeAwsExtendedId(@Nullable String amxRequestId);
}
