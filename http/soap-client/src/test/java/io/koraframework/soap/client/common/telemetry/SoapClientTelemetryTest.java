package io.koraframework.soap.client.common.telemetry;

import io.koraframework.soap.client.common.SoapMethodDescriptor;
import io.koraframework.soap.client.common.telemetry.impl.DefaultSoapClientTelemetry;
import io.koraframework.soap.client.common.telemetry.impl.NoopSoapClientLoggerFactory;
import io.koraframework.soap.client.common.telemetry.impl.NoopSoapClientMetricsFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SoapClientTelemetryTest {

    @Test
    void spanEndsAfterItStarts() throws Exception {
        var ended = new CopyOnWriteArrayList<SpanData>();
        var tracer = SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
            @Override public void onStart(Context parentContext, ReadWriteSpan span) {}
            @Override public boolean isStartRequired() { return false; }
            @Override public void onEnd(ReadableSpan span) { ended.add(span.toSpanData()); }
            @Override public boolean isEndRequired() { return true; }
        }).build().get("test");
        var config = new $SoapClientTelemetryConfig_ConfigValueMapper.SoapClientTelemetryConfig_Impl(
            new $SoapClientTelemetryConfig_SoapClientLoggingConfig_ConfigValueMapper.SoapClientLoggingConfig_Defaults(),
            new $SoapClientTelemetryConfig_SoapClientMetricsConfig_ConfigValueMapper.SoapClientMetricsConfig_Defaults(),
            new $SoapClientTelemetryConfig_SoapClientTracingConfig_ConfigValueMapper.SoapClientTracingConfig_Defaults());
        var telemetry = new DefaultSoapClientTelemetry("soap", "foo.Service", config,
            new SoapMethodDescriptor("foo.Service", "Service", "op", "op"), "http://localhost:8080/ws",
            tracer, new SimpleMeterRegistry(), NoopSoapClientMetricsFactory.INSTANCE, NoopSoapClientLoggerFactory.INSTANCE);

        var observation = telemetry.observe(null);
        Thread.sleep(10);
        observation.end();

        assertThat(ended).hasSize(1);
        var span = ended.get(0);
        assertThat(span.getEndEpochNanos())
            .as("end=%d start=%d", span.getEndEpochNanos(), span.getStartEpochNanos())
            .isGreaterThanOrEqualTo(span.getStartEpochNanos());
    }
}
