package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

import java.util.Collection;

public final class DefaultS3Telemetry implements S3Telemetry {

    private final S3Logger logger;
    private final S3Metrics metrics;
    private final S3Tracer tracer;

    public DefaultS3Telemetry(@Nullable S3Tracer tracer,
                              @Nullable S3Metrics metrics,
                              @Nullable S3Logger logger) {
        this.logger = logger;
        this.tracer = tracer;
        this.metrics = metrics;
    }

    public enum S3Operation {
        PUT_OBJECT, START_MULTIPART_UPLOAD, PUT_OBJECT_PART, COMPLETE_MULTIPART_UPLOAD, ABORT_MULTIPART_UPLOAD,
        DELETE_OBJECT, GET_OBJECT, HEAD_OBJECT, LIST_OBJECTS
    }

    private class TelemetryContext implements S3TelemetryContext {
        private final long startNanos = System.nanoTime();
        private final S3Operation operation;
        private final String bucket;
        @Nullable
        private final String key;
        private String awsRequestId;
        private String awsExtendedRequestId;
        private String uploadId;
        private Throwable error;
        private long contentLength = -1;
        private int partNumber;

        private TelemetryContext(S3Operation operation, String bucket, @Nullable String key) {
            this.bucket = bucket;
            this.key = key;
            this.operation = operation;
        }

        @Override
        public void setAwsRequestId(String awsRequestId) {
            this.awsRequestId = awsRequestId;
        }

        @Override
        public void setAwsExtendedId(String awsRequestId) {
            this.awsExtendedRequestId = awsRequestId;
        }

        @Override
        public void setUploadId(String awsRequestId) {
            this.uploadId = awsRequestId;
        }

        @Override
        public void setError(Throwable throwable) {
            this.error = throwable;
        }

        public void setContentLength(long contentLength) {
            this.contentLength = contentLength;
        }

        public void setPartNumber(int partNumber) {
            this.partNumber = partNumber;
        }

        @Override
        public void close() {

        }
    }

    @Override
    public S3TelemetryContext putObject(String bucket, String key, long contentLength) {
        var ctx = new TelemetryContext(S3Operation.PUT_OBJECT, bucket, key);
        ctx.setContentLength(contentLength);
        return ctx;
    }

    @Override
    public S3TelemetryContext startMultipartUpload(String bucket, String key) {
        return new TelemetryContext(S3Operation.START_MULTIPART_UPLOAD, bucket, key);
    }

    @Override
    public S3TelemetryContext putObjectPart(String bucket, String key, String uploadId, int partNumber, long contentLength) {
        var ctx = new TelemetryContext(S3Operation.PUT_OBJECT_PART, bucket, key);
        ctx.setUploadId(uploadId);
        ctx.setContentLength(contentLength);
        ctx.setPartNumber(partNumber);
        return ctx;
    }

    @Override
    public S3TelemetryContext completeMultipartUpload(String bucket, String key, String uploadId) {
        var ctx = new TelemetryContext(S3Operation.COMPLETE_MULTIPART_UPLOAD, bucket, key);
        ctx.setUploadId(uploadId);
        return ctx;
    }

    @Override
    public S3TelemetryContext abortMultipartUpload(String bucket, String key, String uploadId) {
        var ctx = new TelemetryContext(S3Operation.ABORT_MULTIPART_UPLOAD, bucket, key);
        ctx.setUploadId(uploadId);
        return ctx;
    }

    @Override
    public S3TelemetryContext getObject(String bucket, String key) {
        return new TelemetryContext(S3Operation.GET_OBJECT, bucket, key);
    }

    @Override
    public S3TelemetryContext getMetadata(String bucket, String key) {
        return new TelemetryContext(S3Operation.HEAD_OBJECT, bucket, key);
    }

    @Override
    public S3TelemetryContext listMetadata(String bucket, String prefix, String delimiter) {
        return new TelemetryContext(S3Operation.LIST_OBJECTS, bucket, prefix);
    }

    @Override
    public S3TelemetryContext deleteObject(String bucket, String key) {
        return new TelemetryContext(S3Operation.DELETE_OBJECT, bucket, key);
    }

    @Override
    public S3TelemetryContext deleteObjects(String bucket, Collection<String> keys) {
        return new TelemetryContext(S3Operation.DELETE_OBJECT, bucket, null);
    }

//    @Override
//    public S3ClientTelemetryContext get() {
//        var start = System.nanoTime();
//        final S3ClientTracer.S3ClientSpan span;
//        if (tracer != null) {
//            span = tracer.createSpan();
//        } else {
//            span = null;
//        }
//
//        return new S3ClientTelemetryContext() {
//
//            @Override
//            public void prepared(String method, String bucket, @Nullable String key, @Nullable Long contentLength) {
//                if (logger != null) {
//                    logger.logRequest(method, bucket, key, contentLength);
//                }
//                if (span != null) {
//                    span.prepared(method, bucket, key, contentLength);
//                }
//            }
//
//            @Override
//            public void close(String method, String bucket, @Nullable String key, int statusCode, @Nullable S3Exception exception) {
//                var end = System.nanoTime();
//                var processingTime = end - start;
//                if (metrics != null) {
//                    metrics.record(method, bucket, key, statusCode, processingTime, exception);
//                }
//                if (logger != null) {
//                    logger.logResponse(method, bucket, key, statusCode, processingTime, exception);
//                }
//                if (span != null) {
//                    span.close(statusCode, exception);
//                }
//            }
//        };
//    }
}
