package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingTelemetryFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingTelemetryTest {

    @ParameterizedTest
    @ValueSource(strings = {"jdk", "quartz", "dbscheduler"})
    void schedulerTypeIsReportedToMetricsAndTracing(String schedulerType) {
        var registry = new SimpleMeterRegistry();
        var spans = new CopyOnWriteArrayList<SpanData>();
        try (var tracerProvider = SdkTracerProvider.builder().addSpanProcessor(collector(spans)).build()) {
            var factory = new DefaultSchedulingTelemetryFactory(config(), tracerProvider.get("test"), registry, null, null);
            var telemetry = factory.get(schedulerType, "jobs.job", null, SchedulingTelemetryTest.class, "job");

            var observation = telemetry.observe();
            observation.observeRun();
            observation.end();
        }

        var timer = registry.get("scheduling.job.duration").tag(DefaultSchedulingTelemetry.SCHEDULING_SYSTEM, schedulerType).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(spans).singleElement()
            .satisfies(span -> assertThat(span.getAttributes().get(AttributeKey.stringKey(DefaultSchedulingTelemetry.SCHEDULING_SYSTEM))).isEqualTo(schedulerType));
    }

    private static SchedulingTelemetryConfig config() {
        return new SchedulingTelemetryConfig() {
            @Override
            public SchedulingLoggingConfig logging() {
                return new SchedulingLoggingConfig() {};
            }

            @Override
            public SchedulingMetricsConfig metrics() {
                return new SchedulingMetricsConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }
                };
            }

            @Override
            public SchedulingTracingConfig tracing() {
                return new SchedulingTracingConfig() {};
            }
        };
    }

    private static SpanProcessor collector(List<SpanData> spans) {
        return new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {
            }

            @Override
            public boolean isStartRequired() {
                return false;
            }

            @Override
            public void onEnd(ReadableSpan span) {
                spans.add(span.toSpanData());
            }

            @Override
            public boolean isEndRequired() {
                return true;
            }
        };
    }
}
