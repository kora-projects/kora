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

    public static final Key<S3ClientTelemetryContext> CONTEXT_KEY = new KeyImmutable<>() {};

    private static final ExecutionAttribute<S3ClientTelemetryContext> CONTEXT = new ExecutionAttribute<>("kora-s3-telemetry-context");

    private final S3ClientTelemetry telemetry;

    public AwsS3ClientTelemetryInterceptor(S3ClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = ctx.get(CONTEXT_KEY);
        if (telemetryContext == null) {
            telemetryContext = telemetry.get(null, null);
        }
        executionAttributes.putAttribute(CONTEXT, telemetryContext);
    }

    @Override
    public void afterMarshalling(Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            SdkHttpRequest request = context.httpRequest();
            Long contentLength = context.requestBody().flatMap(RequestBody::optionalContentLength)
                .or(() -> context.asyncRequestBody().flatMap(AsyncRequestBody::contentLength))
                .orElse(null);

            telemetryContext.prepared(request.method().name(), request.encodedPath(), request.getUri(), request.host(), request.port(), contentLength);
        }
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var httpResponse = context.response().sdkHttpResponse();
            telemetryContext.close(httpResponse.statusCode());
        }
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes executionAttributes) {
        var telemetryContext = executionAttributes.getAttribute(CONTEXT);
        if (telemetryContext != null) {
            var statusCode = context.response()
                .map(SdkResponse::sdkHttpResponse)
                .map(SdkHttpResponse::statusCode)
                .orElse(-1);

            telemetryContext.close(statusCode, context.exception());
        }
    }
}
