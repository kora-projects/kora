package io.koraframework.s3.client.aws;

import io.koraframework.s3.client.aws.telemetry.AwsS3ClientObservation;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetry;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.s3.model.*;

public final class AwsS3ClientTelemetryInterceptor implements ExecutionInterceptor {

    private static final ExecutionAttribute<AwsS3ClientObservation> OBSERVATION = new ExecutionAttribute<>("s3-aws-telemetry-observation");

    private final AwsS3ClientTelemetry telemetry;

    public AwsS3ClientTelemetryInterceptor(AwsS3ClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution execContext, ExecutionAttributes executionAttributes) {
        var bucket = getBucket(execContext.request());
        var operation = getOperation(execContext.request());
        var observation = telemetry.observe(operation, bucket);
        executionAttributes.putAttribute(OBSERVATION, observation);
    }

    @Override
    public void afterExecution(Context.AfterExecution execContext, ExecutionAttributes executionAttributes) {
        var observation = executionAttributes.getAttribute(OBSERVATION);
        if (observation != null) {
            execContext.httpResponse().firstMatchingHeader("x-amz-request-id").ifPresent(observation::observeAwsRequestId);
            execContext.httpResponse().firstMatchingHeader("x-amz-extended-request-id").ifPresent(observation::observeAwsRequestId);

            var key = extractKey(execContext.request());
            if (key != null) {
                observation.observeKey(key);
            }

            var uploadId = extractUploadId(execContext.request(), execContext.response());
            if (uploadId != null) {
                observation.observeUploadId(uploadId);
            }

            observation.end();
        }
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution execContext, ExecutionAttributes executionAttributes) {
        var observation = executionAttributes.getAttribute(OBSERVATION);
        if (observation != null) {
            if (execContext.httpResponse().isPresent()) {
                var httpResponse = execContext.httpResponse().get();
                httpResponse.firstMatchingHeader("x-amz-request-id").ifPresent(observation::observeAwsRequestId);
                httpResponse.firstMatchingHeader("x-amz-extended-request-id").ifPresent(observation::observeAwsRequestId);
            }

            if (execContext.response().isPresent()) {
                var response = execContext.response().get();
                var uploadId = extractUploadId(execContext.request(), response);
                if (uploadId != null) {
                    observation.observeUploadId(uploadId);
                }
            } else {
                var uploadId = extractUploadId(execContext.request(), null);
                if (uploadId != null) {
                    observation.observeUploadId(uploadId);
                }
            }

            var key = extractKey(execContext.request());
            if (key != null) {
                observation.observeKey(key);
            }

            observation.observeError(execContext.exception());
            observation.end();
        }
    }

    private static String getBucket(SdkRequest request) {
        return switch (request) {
            // Операции с объектами
            case GetObjectRequest req -> req.bucket();
            case GetObjectAttributesRequest req -> req.bucket();
            case PutObjectRequest req -> req.bucket();
            case DeleteObjectRequest req -> req.bucket();
            case HeadObjectRequest req -> req.bucket();
            case CopyObjectRequest req -> req.destinationBucket();
            case UploadPartRequest req -> req.bucket();
            case UploadPartCopyRequest req -> req.destinationBucket();
            case AbortMultipartUploadRequest req -> req.bucket();
            case CompleteMultipartUploadRequest req -> req.bucket();
            case CreateMultipartUploadRequest req -> req.bucket();

            // Операции с бакетами
            case ListObjectsRequest req -> req.bucket();
            case ListObjectsV2Request req -> req.bucket();
            case ListObjectVersionsRequest req -> req.bucket();
            case ListMultipartUploadsRequest req -> req.bucket();

            case HeadBucketRequest req -> req.bucket();
            case GetBucketLocationRequest req -> req.bucket();
            case GetBucketAclRequest req -> req.bucket();
            case GetBucketCorsRequest req -> req.bucket();
            case GetBucketLifecycleConfigurationRequest req -> req.bucket();
            case GetBucketPolicyRequest req -> req.bucket();
            case GetBucketReplicationRequest req -> req.bucket();
            case GetBucketTaggingRequest req -> req.bucket();
            case GetBucketVersioningRequest req -> req.bucket();
            case GetBucketEncryptionRequest req -> req.bucket();
            case GetBucketLoggingRequest req -> req.bucket();
            case GetBucketNotificationConfigurationRequest req -> req.bucket();
            case GetPublicAccessBlockRequest req -> req.bucket();
            case GetBucketOwnershipControlsRequest req -> req.bucket();

            case PutBucketAclRequest req -> req.bucket();
            case PutBucketCorsRequest req -> req.bucket();
            case PutBucketLifecycleConfigurationRequest req -> req.bucket();
            case PutBucketPolicyRequest req -> req.bucket();
            case PutBucketReplicationRequest req -> req.bucket();
            case PutBucketTaggingRequest req -> req.bucket();
            case PutBucketVersioningRequest req -> req.bucket();
            case PutBucketEncryptionRequest req -> req.bucket();
            case PutBucketLoggingRequest req -> req.bucket();
            case PutBucketNotificationConfigurationRequest req -> req.bucket();
            case PutPublicAccessBlockRequest req -> req.bucket();
            case PutBucketOwnershipControlsRequest req -> req.bucket();

            case DeleteBucketCorsRequest req -> req.bucket();
            case DeleteBucketPolicyRequest req -> req.bucket();
            case DeleteBucketReplicationRequest req -> req.bucket();
            case DeleteBucketTaggingRequest req -> req.bucket();
            case DeletePublicAccessBlockRequest req -> req.bucket();
            case DeleteBucketOwnershipControlsRequest req -> req.bucket();

            // Удаление бакета
            case DeleteBucketRequest req -> req.bucket();

            // Создание бакета
            case CreateBucketRequest req -> req.bucket();

            // Others
            case GetObjectLegalHoldRequest req -> req.bucket();
            case PutObjectLegalHoldRequest req -> req.bucket();
            case GetObjectRetentionRequest req -> req.bucket();
            case PutObjectRetentionRequest req -> req.bucket();
            case GetObjectTaggingRequest req -> req.bucket();
            case PutObjectTaggingRequest req -> req.bucket();
            case DeleteObjectTaggingRequest req -> req.bucket();
            case RestoreObjectRequest req -> req.bucket();
            case SelectObjectContentRequest req -> req.bucket();

            default -> request.getValueForField("Bucket", String.class).orElse("unknown");
        };
    }

    private static String getOperation(SdkRequest request) {
        return switch (request) {
            // CRUD объектов
            case GetObjectRequest req -> "GetObject";
            case GetObjectAttributesRequest req -> "GetObjectAttributes";
            case PutObjectRequest req -> "PutObject";
            case HeadObjectRequest req -> "HeadObject";
            case DeleteObjectRequest req -> "DeleteObject";
            case DeleteObjectsRequest req -> "DeleteObjects";
            case CopyObjectRequest req -> "CopyObject";

            // Multipart Upload
            case CreateMultipartUploadRequest req -> "CreateMultipartUpload";
            case UploadPartRequest req -> "UploadPart";
            case UploadPartCopyRequest req -> "UploadPartCopy";
            case CompleteMultipartUploadRequest req -> "CompleteMultipartUpload";
            case AbortMultipartUploadRequest req -> "AbortMultipartUpload";

            // List операции
            case ListObjectsRequest req -> "ListObjects";
            case ListObjectsV2Request req -> "ListObjectsV2";
            case ListObjectVersionsRequest req -> "ListObjectVersions";
            case ListMultipartUploadsRequest req -> "ListMultipartUploads";

            // Бакеты
            case CreateBucketRequest req -> "CreateBucket";
            case DeleteBucketRequest req -> "DeleteBucket";
            case HeadBucketRequest req -> "HeadBucket";

            // Bucket Configuration
            case GetBucketLocationRequest req -> "GetBucketLocation";
            case GetBucketAclRequest req -> "GetBucketAcl";
            case GetBucketCorsRequest req -> "GetBucketCors";
            case GetBucketLifecycleConfigurationRequest req -> "GetLifecycleConfiguration";
            case PutBucketLifecycleConfigurationRequest req -> "PutLifecycleConfiguration";
            case GetBucketPolicyRequest req -> "GetBucketPolicy";
            case PutBucketPolicyRequest req -> "PutBucketPolicy";
            case GetBucketReplicationRequest req -> "GetReplicationConfiguration";
            case PutBucketReplicationRequest req -> "PutReplicationConfiguration";
            case GetBucketTaggingRequest req -> "GetBucketTagging";
            case PutBucketTaggingRequest req -> "PutBucketTagging";
            case DeleteBucketTaggingRequest req -> "DeleteBucketTagging";
            case GetBucketVersioningRequest req -> "GetBucketVersioning";
            case PutBucketVersioningRequest req -> "PutBucketVersioning";
            case GetBucketEncryptionRequest req -> "GetBucketEncryption";
            case PutBucketEncryptionRequest req -> "PutBucketEncryption";
            case GetBucketLoggingRequest req -> "GetBucketLogging";
            case PutBucketLoggingRequest req -> "PutBucketLogging";
            case GetPublicAccessBlockRequest req -> "GetPublicAccessBlock";
            case PutPublicAccessBlockRequest req -> "PutPublicAccessBlock";
            case DeletePublicAccessBlockRequest req -> "DeletePublicAccessBlock";
            case GetBucketOwnershipControlsRequest req -> "GetBucketOwnershipControls";
            case PutBucketOwnershipControlsRequest req -> "PutBucketOwnershipControls";
            case DeleteBucketOwnershipControlsRequest req -> "DeleteBucketOwnershipControls";

            // Object Tagging
            case GetObjectTaggingRequest req -> "GetObjectTagging";
            case PutObjectTaggingRequest req -> "PutObjectTagging";
            case DeleteObjectTaggingRequest req -> "DeleteObjectTagging";

            // Object Retention & Legal Hold
            case GetObjectRetentionRequest req -> "GetObjectRetention";
            case PutObjectRetentionRequest req -> "PutObjectRetention";
            case GetObjectLegalHoldRequest req -> "GetObjectLegalHold";
            case PutObjectLegalHoldRequest req -> "PutObjectLegalHold";

            // Дополнительные операции
            case RestoreObjectRequest req -> "RestoreObject";
            case SelectObjectContentRequest req -> "SelectObjectContent";
            case WriteGetObjectResponseRequest req -> "WriteGetObjectResponse"; // для S3 Object Lambda

            default -> "unknown";
        };
    }

    @Nullable
    private static String extractKey(SdkRequest request) {
        return switch (request) {
            case GetObjectRequest req -> req.key();
            case GetObjectAttributesRequest req -> req.key();
            case PutObjectRequest req -> req.key();
            case HeadObjectRequest req -> req.key();
            case DeleteObjectRequest req -> req.key();
            case AbortMultipartUploadRequest req -> req.key();
            case CompleteMultipartUploadRequest req -> req.key();
            case CreateMultipartUploadRequest req -> req.key();
            case UploadPartRequest req -> req.key();
            case RestoreObjectRequest req -> req.key();
            case SelectObjectContentRequest req -> req.key();
            case GetObjectTaggingRequest req -> req.key();
            case PutObjectTaggingRequest req -> req.key();
            case DeleteObjectTaggingRequest req -> req.key();
            case GetObjectRetentionRequest req -> req.key();
            case PutObjectRetentionRequest req -> req.key();
            case GetObjectLegalHoldRequest req -> req.key();
            case PutObjectLegalHoldRequest req -> req.key();

            case CopyObjectRequest req -> extractKeyFromCopySource(req.copySource());
            case UploadPartCopyRequest req -> extractKeyFromCopySource(req.copySource());

            case WriteGetObjectResponseRequest req -> req.requestToken(); // не key, но полезный идентификатор

            default -> null;
        };
    }

    private static String extractKeyFromCopySource(String copySource) {
        if (copySource == null || copySource.isEmpty()) {
            return null;
        }

        String source = copySource.startsWith("/") ? copySource.substring(1) : copySource;
        int slashIndex = source.indexOf('/');
        return slashIndex >= 0 ? source.substring(slashIndex + 1) : null;
    }

    private static String extractUploadId(SdkRequest request, SdkResponse response) {
        if (request != null) {
            return switch (request) {
                case UploadPartRequest req -> req.uploadId();
                case UploadPartCopyRequest req -> req.uploadId();
                case CompleteMultipartUploadRequest req -> req.uploadId();
                case AbortMultipartUploadRequest req -> req.uploadId();
                default -> null;
            };
        }

        if (response instanceof CreateMultipartUploadResponse createResp) {
            return createResp.uploadId();
        }

        return null;
    }
}
