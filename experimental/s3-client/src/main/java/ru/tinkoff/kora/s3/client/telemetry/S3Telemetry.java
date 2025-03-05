package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

import java.util.Collection;

public interface S3Telemetry {

    interface S3TelemetryContext extends AutoCloseable {
        void setAwsRequestId(String awsRequestId);

        void setAwsExtendedId(String awsRequestId);

        void setUploadId(String awsRequestId);

        void setError(Throwable throwable);

        void close();
    }

    S3TelemetryContext getObject(String bucket, String key);

    S3TelemetryContext getMetadata(String bucket, String key);

    S3TelemetryContext listMetadata(String bucket, @Nullable String prefix, @Nullable String delimiter);

    S3TelemetryContext deleteObject(String bucket, String key);

    S3TelemetryContext deleteObjects(String bucket, Collection<String> keys);

    S3TelemetryContext putObject(String bucket, String key, long contentLength);

    S3TelemetryContext startMultipartUpload(String bucket, String key);

    S3TelemetryContext putObjectPart(String bucket, String key, String uploadId, int partNumber, long contentLength);

    S3TelemetryContext completeMultipartUpload(String bucket, String key, String uploadId);

    S3TelemetryContext abortMultipartUpload(String bucket, String key, String uploadId);

}
