package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTracer;

import java.net.URI;

public final class OpentelemetryS3ClientTracer implements S3ClientTracer {

    private final String clientName;
    private final Tracer tracer;

    public OpentelemetryS3ClientTracer(String clientName, Tracer tracer) {
        this.clientName = clientName;
        this.tracer = tracer;
    }

    @Override
    public S3ClientSpan createSpan(@Nullable String operation, @Nullable String bucket) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var tctx = OpentelemetryContext.get(ctx);
        var opName = (operation == null || bucket == null)
            ? "S3 CALL"
            : "S3 " + operation + " : " + bucket;

        var span = this.tracer.spanBuilder(opName)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(tctx.getContext())
            .startSpan();

        return new S3ClientSpan() {
            @Override
            public void prepared(String method, String path, URI uri, String host, int port, @Nullable Long contentLength) {
                if (operation == null || bucket == null) {
                    span.updateName("S3 " + method + " " + path);
                }
                span.setAttribute(SemanticAttributes.HTTP_METHOD, method);
                span.setAttribute(SemanticAttributes.URL_PATH, path);
                span.setAttribute(SemanticAttributes.SERVER_ADDRESS, host);
                span.setAttribute(SemanticAttributes.SERVER_PORT, port);
                span.setAttribute(SemanticAttributes.URL_FULL, uri.toString());
                span.setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, method);
                if (contentLength != null) {
                    span.setAttribute(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, contentLength);
                }
            }

            @Override
            public void close(int statusCode, @Nullable Throwable exception) {
                span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
                if (exception != null) {
                    span.recordException(exception);
                }
                span.end();
            }
        };
    }
}
