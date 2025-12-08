package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

import java.util.Map;
import java.util.Set;

final class WrappedJobHandler implements JobHandler {
    private static final TextMapGetter<Map<String, String>> TEXT_MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Set<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        @Nullable
        public String get(@Nullable Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final ZeebeWorkerTelemetry telemetry;
    private final KoraJobWorker jobHandler;

    public WrappedJobHandler(ZeebeWorkerTelemetry telemetry, KoraJobWorker jobHandler) {
        this.telemetry = telemetry;
        this.jobHandler = jobHandler;

    }

    @Override
    public void handle(JobClient client, ActivatedJob job) {
        var jobContext = new ActiveJobContext(jobHandler.type(), job);

        var rootCtx = W3CTraceContextPropagator.getInstance().extract(
            Context.root(),
            job.getCustomHeaders(),
            TEXT_MAP_GETTER
        );
        ScopedValue.where(OpentelemetryContext.VALUE, rootCtx).run(() -> {
            var observation = telemetry.observe(job);
            var mdc = new ru.tinkoff.kora.logging.common.MDC();

            MDC.clear();
            ScopedValue.where(ru.tinkoff.kora.logging.common.MDC.VALUE, mdc)
                .where(OpentelemetryContext.VALUE, Context.root())
                .where(JobContext.VALUE, jobContext)
                .run(() -> {
                    try {
                        observation.observeHandle(jobHandler.type(), job);
                        var command = jobHandler.handle(client, job);
                        observation.observeFinalCommandStep(command);
                        command
                            .send()
                            .join();
                    } catch (Throwable t) {
                        observation.observeError(t);
                        throw t;
                    } finally {
                        observation.end();
                    }
                });
        });
    }
}
