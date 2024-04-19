package ru.tinkoff.kora.opentelemetry.module.s3.client.aws;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public final class S3AwsTracingExecutionInterceptor {

    private static final ExecutionAttribute<Span> SPAN = new ExecutionAttribute<>("rtd-s3-span");
    private final Tracer tracer;

    public S3AwsTracingExecutionInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var tctx = OpentelemetryContext.get(ctx);
        var span = this.tracer.spanBuilder("S3 CALL")
            .setParent(tctx.getContext())
            .startSpan();
        executionAttributes.putAttribute(SPAN, span);
    }

    public void afterExecution(Span span, int statusCode) {
        if (span != null) {
            span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            span.end();
        }
    }

    public void afterMarshalling(Span span, String methodName, String path, @Nullable Long contentLength) {
        if (span != null) {
            span.updateName("S3 " + methodName + " " + path);
//            span.setAttribute(SemanticAttributes.SERVER_ADDRESS, context.httpRequest().host());
//            span.setAttribute(SemanticAttributes.SERVER_PORT, context.httpRequest().port());
//            span.setAttribute(SemanticAttributes.URL_FULL, context.httpRequest().getUri().toString());
            span.setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, methodName);
            span.setAttribute(SemanticAttributes.URL_PATH, path);

            if (contentLength != null) {
                span.setAttribute(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, contentLength);
            }
        }
    }

    public void onExecutionFailure(Span span, Throwable exception) {
        if (span != null) {
            span.recordException(exception);
            span.end();
        }
    }
}
