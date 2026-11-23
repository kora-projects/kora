package io.koraframework.camunda.zeebe.worker;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerObservation;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.config.common.mapper.ConfigValueMapperModule;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ZeebeWorkerRuntimeTests {

    private static ZeebeWorkerConfig parseConfig(Map<String, ?> job) {
        var module = new ConfigValueMapperModule() {};
        var duration = module.durationConfigValueMapper();
        var jobMapper = new $ZeebeWorkerConfig_JobConfig_ConfigValueMapper(
            new $ZeebeWorkerConfig_BackoffConfig_ConfigValueMapper(duration),
            module.listConfigValueMapper(module.stringConfigValueMapper()),
            duration
        );
        var mapper = new $ZeebeWorkerConfig_ConfigValueMapper(module.mapConfigValueMapper(module.stringConfigValueMapper(), jobMapper));
        return mapper.map(ConfigMappingUtils.fromMap(Map.of("job", job)).root());
    }

    @Test
    void namedSectionInheritsNameFromDefaultSection() {
        var config = parseConfig(Map.of(
            "default", Map.of("name", "billing-service"),
            "foo", Map.of("timeout", "30s")
        ));

        var fooCfg = config.getJobConfig("foo");
        assertThat(fooCfg.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(fooCfg.name()).isEqualTo("billing-service");
        assertThat(config.getJobConfig("bar").name()).isEqualTo("billing-service");
    }

    @Test
    void workerNameDefaultsWhenNotConfigured() {
        var config = parseConfig(Map.of("foo", Map.of("timeout", "30s")));

        assertThat(config.getJobConfig("foo").name()).isEqualTo("default");
        assertThat(config.getJobConfig("bar").name()).isEqualTo("default");
    }

    @Test
    void namedSectionOverridesDefaultName() {
        var config = parseConfig(Map.of(
            "default", Map.of("name", "billing-service"),
            "foo", Map.of("name", "foo-service")
        ));

        assertThat(config.getJobConfig("foo").name()).isEqualTo("foo-service");
    }

    @Test
    void scopedJobContextJobNameIsConfiguredName() {
        var seen = new AtomicReference<JobContext>();
        var job = Mockito.mock(ActivatedJob.class);
        var client = mockClient(job);
        KoraJobWorker worker = new KoraJobWorker() {
            @Override
            public String type() {
                return "foo";
            }

            @Override
            public FinalCommandStep<?> handle(JobClient c, ActivatedJob j) {
                seen.set(JobContext.VALUE.get());
                return c.newCompleteCommand(j);
            }
        };

        new WrappedJobHandler(j -> noopObservation(Span.getInvalid()), worker, "my-worker-name").handle(client, job);

        assertThat(seen.get().jobName()).isEqualTo("my-worker-name");
        assertThat(seen.get().jobType()).isEqualTo("foo");
    }

    @Test
    void handlerRunsInsideJobSpanContext() {
        var spanCtx = SpanContext.create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled(), TraceState.getDefault());
        var jobSpan = Span.wrap(spanCtx);
        var seen = new AtomicReference<Span>();
        var seenObservation = new AtomicReference<Observation>();
        var job = Mockito.mock(ActivatedJob.class);
        var client = mockClient(job);
        KoraJobWorker worker = new KoraJobWorker() {
            @Override
            public String type() {
                return "foo";
            }

            @Override
            public FinalCommandStep<?> handle(JobClient c, ActivatedJob j) {
                seen.set(Span.fromContext(OpentelemetryContext.VALUE.get()));
                seenObservation.set(Observation.VALUE.get());
                return c.newCompleteCommand(j);
            }
        };
        var observation = noopObservation(jobSpan);
        ZeebeWorkerTelemetry telemetry = j -> observation;

        new WrappedJobHandler(telemetry, worker, "foo").handle(client, job);

        assertThat(seen.get().getSpanContext()).isEqualTo(spanCtx);
        assertThat(seenObservation.get()).isSameAs(observation);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JobClient mockClient(ActivatedJob job) {
        var client = Mockito.mock(JobClient.class);
        var step = Mockito.mock(CompleteJobCommandStep1.class);
        var future = Mockito.mock(CamundaFuture.class);
        Mockito.when(step.send()).thenReturn(future);
        Mockito.when(client.newCompleteCommand(job)).thenReturn(step);
        Mockito.when(job.getCustomHeaders()).thenReturn(Map.of());
        Mockito.when(job.getType()).thenReturn("foo");
        return client;
    }

    private static ZeebeWorkerObservation noopObservation(Span span) {
        return new ZeebeWorkerObservation() {
            @Override
            public void observeFinalCommandStep(FinalCommandStep<?> command) {}

            @Override
            public void observeHandle(String type, ActivatedJob job) {}

            @Override
            public Span span() {
                return span;
            }

            @Override
            public void end() {}

            @Override
            public void observeError(Throwable e) {}
        };
    }
}
