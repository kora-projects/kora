package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.common.Context.Key;
import ru.tinkoff.kora.common.Context.KeyImmutable;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry.S3ClientTelemetryContext;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public final class AwsS3ClientTelemetryInterceptor implements ExecutionInterceptor {

    public static final Key<Operation> OPERATION_KEY = new KeyImmutable<>() {};

    public record Operation(String name, String bucket) {}

    private static final ExecutionAttribute<S3ClientTelemetryContext> CONTEXT = new ExecutionAttribute<>("kora-s3-telemetry-context");

    private final S3ClientTelemetry telemetry;

    public AwsS3ClientTelemetryInterceptor(S3ClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution execContext, ExecutionAttributes executionAttributes) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var operation = ctx.get(OPERATION_KEY);

        final S3ClientTelemetryContext telemetryContext;
        if (operation != null) {
            telemetryContext = telemetry.get(operation.name(), operation.bucket());
        } else {
            telemetryContext = telemetry.get(null, null);
        }

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

            telemetryContext.prepared(request.method().name(), request.encodedPath(), request.getUri(), request.host(), request.port(), contentLength);
        }
    }

    @Override
    public void afterExecution(Context.AfterExecution execContext, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var httpResponse = execContext.response().sdkHttpResponse();
            telemetryContext.close(httpResponse.statusCode());
        }
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution execContext, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var statusCode = execContext.response()
                .map(SdkResponse::sdkHttpResponse)
                .map(SdkHttpResponse::statusCode)
                .orElse(-1);

            telemetryContext.close(statusCode, execContext.exception());
        }
    }
}
