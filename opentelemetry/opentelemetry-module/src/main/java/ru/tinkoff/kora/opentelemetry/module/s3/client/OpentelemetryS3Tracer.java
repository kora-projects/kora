package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.s3.client.exception.S3ClientErrorException;
import ru.tinkoff.kora.s3.client.telemetry.S3Tracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

//https://opentelemetry.io/docs/specs/semconv/object-stores/s3/
public final class OpentelemetryS3Tracer implements S3Tracer {
    private static final AttributeKey<String> CLIENT_NAME = stringKey("aws.client.name");
    private static final AttributeKey<String> ERROR_CODE = stringKey("aws.error.code");

    private final Class<?> client;
    private final Tracer tracer;

    public OpentelemetryS3Tracer(Class<?> client, Tracer tracer) {
        this.client = client;
        this.tracer = tracer;
    }

    @Override
    public S3Tracer.S3Span createSpan(String operation, String bucket, @Nullable String key, @Nullable Long contentLength) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var tctx = OpentelemetryContext.get(ctx);

        var span = this.tracer.spanBuilder("S3 " + client + " " + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(tctx.getContext())
            .startSpan();

        span.setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api");
        span.setAttribute(RpcIncubatingAttributes.RPC_SERVICE, "s3");
        span.setAttribute(RpcIncubatingAttributes.RPC_METHOD, operation);
        span.setAttribute(OpentelemetryS3Tracer.CLIENT_NAME, client.getSimpleName());
        span.setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
        if (key != null) {
            span.setAttribute(AwsIncubatingAttributes.AWS_S3_KEY, key);
        }
        if (contentLength != null) {
            span.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, contentLength);
        }

        return exception -> {
            if (exception != null) {
                span.setStatus(StatusCode.ERROR);
                span.recordException(exception);
                if (exception instanceof S3ClientErrorException error) {
                    span.setAttribute(ERROR_CODE.getKey(), error.getErrorCode());
                }
            }
            span.setAttribute(AwsIncubatingAttributes.AWS_REQUEST_ID, key);
            span.end();
        };
    }
}
