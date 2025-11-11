package ru.tinkoff.kora.aws.s3.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface S3ClientObservation extends Observation {
    void observeKey(String key);

    void observeUploadId(String uploadId);

    void observeAwsRequestId(@Nullable String amxRequestId);

    void observeAwsExtendedId(@Nullable String amxRequestId);
}
