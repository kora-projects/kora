package io.koraframework.camunda.zeebe.worker;

import io.camunda.client.api.response.*;
import io.camunda.client.api.worker.*;
import io.koraframework.camunda.zeebe.worker.telemetry.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.opentelemetry.api.trace.propagation.*;
import io.opentelemetry.context.*;
import io.opentelemetry.context.propagation.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static io.koraframework.common.telemetry.OpentelemetryContextStorage.*;
import static io.koraframework.logging.common.MDC.*;

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
        var jobContextData = new ActiveJobContext(jobHandler.type(), job);

        var rootCtx = W3CTraceContextPropagator.getInstance().extract(
            Context.root(),
            job.getCustomHeaders(),
            TEXT_MAP_GETTER
        );

        var observation = telemetry.observe(job);

        var baseContext = KoraContext.empty()
            .with(OPENTELEMETRY_KEY, rootCtx)
            .with(MDC_KEY, new io.koraframework.logging.common.MDC())
            .with(JobContext.KEY, jobContextData);

        var finalContext = Observation.attach(baseContext, observation);

        MDC.clear();

        KoraContext.runWith(finalContext, new JobHandlerRunner(this, client, job, observation));
    }

    private static final class JobHandlerRunner implements Supplier<Void> {
        private final WrappedJobHandler wrapper;
        private final JobClient client;
        private final ActivatedJob job;
        private final ZeebeWorkerObservation observation;

        public JobHandlerRunner(WrappedJobHandler wrapper,
                                JobClient client,
                                ActivatedJob job,
                                ZeebeWorkerObservation observation) {
            this.wrapper = wrapper;
            this.client = client;
            this.job = job;
            this.observation = observation;
        }

        @Override
        public Void get() {
            try {
                observation.observeHandle(wrapper.jobHandler.type(), job);
                var command = wrapper.jobHandler.handle(client, job);
                observation.observeFinalCommandStep(command);
                command.send().join();
            } catch (Throwable t) {
                observation.observeError(t);
                throw t;
            } finally {
                observation.end();
            }
            return null;
        }
    }
}
