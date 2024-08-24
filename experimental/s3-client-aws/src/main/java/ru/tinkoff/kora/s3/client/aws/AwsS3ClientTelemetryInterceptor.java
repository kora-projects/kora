package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.common.Context.Key;
import ru.tinkoff.kora.common.Context.KeyImmutable;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry.S3ClientTelemetryContext;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

@ApiStatus.Experimental
public final class AwsS3ClientTelemetryInterceptor implements ExecutionInterceptor {

    record Operation(String method, String bucket, String key) {}

    private static final ExecutionAttribute<S3ClientTelemetryContext> CONTEXT = new ExecutionAttribute<>("kora-s3-telemetry-context");
    private static final ExecutionAttribute<Operation> OPERATION = new ExecutionAttribute<>("kora-s3-telemetry-operation");

    private final S3ClientTelemetry telemetry;
    private final AwsS3ClientConfig.AddressStyle addressStyle;

    public AwsS3ClientTelemetryInterceptor(S3ClientTelemetry telemetry, AwsS3ClientConfig.AddressStyle addressStyle) {
        this.telemetry = telemetry;
        this.addressStyle = addressStyle;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution execContext, ExecutionAttributes executionAttributes) {
        final S3ClientTelemetryContext telemetryContext = telemetry.get();
        executionAttributes.putAttribute(CONTEXT, telemetryContext);
    }

    @Override
    public void afterMarshalling(Context.AfterMarshalling execContext, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            SdkHttpRequest request = execContext.httpRequest();
            Long contentLength = execContext.requestBody().flatMap(RequestBody::optionalContentLength)
                .or(() -> execContext.asyncRequestBody().flatMap(AsyncRequestBody::contentLength))
                .orElse(null);

            final BucketAndKey bk;
            if(addressStyle == AwsS3ClientConfig.AddressStyle.PATH) {
                bk = getPathAddress(request.encodedPath());
            } else {
                bk = getVirtualHost(request.host(), request.encodedPath());
            }

            telemetryContext.prepared(request.method().name(), bk.bucket(), bk.key(), contentLength);
            executionAttributes.putAttribute(OPERATION, new Operation(request.method().name().toUpperCase(), bk.bucket(), bk.key()));
        }
    }

    private record BucketAndKey(String bucket, @Nullable String key) { }

    private static BucketAndKey getPathAddress(String path) {
        final String bucket;
        final String key;
        final int startFrom = (path.charAt(0) == '/')
            ? 1
            : 0;

        if(path.equals("/")) {
            bucket = "/";
            key = null;
        } else {
            int bucketSeparator = path.indexOf('/', startFrom);
            if (bucketSeparator == -1) {
                bucket = path.substring(startFrom);
                key = null;
            } else {
                bucket = path.substring(startFrom, bucketSeparator);
                key = path.substring(bucketSeparator + 1);
            }
        }

        return new BucketAndKey(bucket, key);
    }

    private static BucketAndKey getVirtualHost(String host, String path) {
        final String bucket;
        final String key;
        final int startFrom = (path.charAt(0) == '/')
            ? 1
            : 0;

        int bucketEnd = host.indexOf('.');
        if(bucketEnd == -1) {
            return getPathAddress(path);
        } else {
            bucket = host.substring(0, bucketEnd);
            if (path.length() == 1) {
                key = null;
            } else {
                key = path.substring(startFrom);
            }
        }

        return new BucketAndKey(bucket, key);
    }

    @Override
    public void afterExecution(Context.AfterExecution execContext, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var httpResponse = execContext.response().sdkHttpResponse();
            var op = executionAttributes.getAttribute(OPERATION);
            telemetryContext.close(op.method(), op.bucket(), op.key(), httpResponse.statusCode());
        }
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution execContext, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var statusCode = execContext.response()
                .map(SdkResponse::sdkHttpResponse)
                .map(SdkHttpResponse::statusCode)
                .or(() -> execContext.httpResponse()
                    .map(SdkHttpResponse::statusCode))
                .orElse(-1);

            final String errorCode;
            final String errorMessage;
            if (execContext.exception() instanceof AwsServiceException ae) {
                errorCode = ae.awsErrorDetails().errorCode();
                errorMessage = ae.awsErrorDetails().errorMessage();
            } else {
                errorCode = execContext.exception().getClass().getSimpleName();
                errorMessage = execContext.exception().getMessage();
            }
            var op = executionAttributes.getAttribute(OPERATION);
            var s3Exception = new S3Exception(execContext.exception(), errorCode, errorMessage);
            telemetryContext.close(op.method(), op.bucket(), op.key(), statusCode, s3Exception);
        }
    }
}
