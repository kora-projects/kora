package io.koraframework.opentelemetry.tracing;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class KoraTracerStatusTest {

    private final CopyOnWriteArrayList<SpanData> spans = new CopyOnWriteArrayList<>();
    private final KoraTracer tracer = new KoraTracer(SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
        @Override public void onStart(Context parentContext, ReadWriteSpan span) {}
        @Override public boolean isStartRequired() { return false; }
        @Override public void onEnd(ReadableSpan span) { spans.add(span.toSpanData()); }
        @Override public boolean isEndRequired() { return true; }
    }).build().get("test"));

    @Test
    void statusSetByCallbackIsKept() {
        // the callback handles a failure itself (e.g. a fallback) and marks its span as failed
        var result = tracer.traceParent("op", span -> {
            span.setStatus(StatusCode.ERROR, "upstream unavailable, served from fallback");
            return "fallback";
        });

        assertThat(result).isEqualTo("fallback");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void spanWithoutStatusEndsOk() {
        var result = tracer.traceParent("op", span -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }
}
