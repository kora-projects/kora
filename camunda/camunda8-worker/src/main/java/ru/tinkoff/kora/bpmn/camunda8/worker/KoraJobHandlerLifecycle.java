package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.bpmn.camunda8.worker.Camunda8WorkerConfig.JobConfig;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetryFactory;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.ZeebeClientWorkerMetricsFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KoraJobHandlerLifecycle implements Lifecycle {

    private final ZeebeClient client;
    private final List<KoraJobWorker> jobWorkers;
    private final Camunda8ClientConfig clientConfig;
    private final Camunda8WorkerConfig workerConfig;
    private final Camunda8BackoffFactory camundaBackoffFactory;
    private final Camunda8WorkerTelemetryFactory telemetryFactory;
    private final ZeebeClientWorkerMetricsFactory zeebeMetricsFactory;

    private final List<JobWorker> workers = new CopyOnWriteArrayList<>();

    public KoraJobHandlerLifecycle(ZeebeClient client,
                                   List<KoraJobWorker> jobWorkers,
                                   Camunda8ClientConfig clientConfig,
                                   Camunda8WorkerConfig workerConfig,
                                   Camunda8BackoffFactory camundaBackoffFactory,
                                   Camunda8WorkerTelemetryFactory telemetryFactory,
                                   @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        this.client = client;
        this.jobWorkers = jobWorkers;
        this.clientConfig = clientConfig;
        this.workerConfig = workerConfig;
        this.camundaBackoffFactory = camundaBackoffFactory;
        this.telemetryFactory = telemetryFactory;
        this.zeebeMetricsFactory = zeebeMetricsFactory;
    }

    @Override
    public void init() {
        CompletableFuture[] jobOpeners = jobWorkers.stream()
            .map(koraJobWorker -> CompletableFuture.runAsync(() -> {
                JobConfig jobConfig = workerConfig.getJobConfig(koraJobWorker.name());
                if (jobConfig.enabled()) {
                    JobWorker jobWorker = createJobWorker(koraJobWorker, jobConfig);
                    workers.add(jobWorker);
                }
            }))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(jobOpeners).join();
    }

    @Override
    public void release() {
        for (JobWorker worker : workers) {
            try {
                worker.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public JobWorker createJobWorker(KoraJobWorker worker, JobConfig jobConfig) {
        final Camunda8WorkerTelemetry telemetry = telemetryFactory.get(clientConfig.telemetry());
        final JobHandler jobHandler = new WrappedJobHandler(telemetry, worker);
        final BackoffSupplier backoffSupplier = camundaBackoffFactory.build(jobConfig.backoff());

        final JobWorkerMetrics jobWorkerMetrics = zeebeMetricsFactory == null
            ? null
            : zeebeMetricsFactory.get(jobConfig.type(), clientConfig.telemetry().metrics());

        var builder = client.newWorker()
            .jobType(jobConfig.type())
            .handler(jobHandler)
            .name(worker.name())
            .metrics(jobWorkerMetrics)
            .fetchVariables(worker.fetchVariables())
            .backoffSupplier(backoffSupplier)
            .timeout(jobConfig.timeout())
            .pollInterval(jobConfig.pollInterval())
            .requestTimeout(jobConfig.requestTimeout())
            .streamEnabled(jobConfig.streamEnabled())
            .streamTimeout(jobConfig.streamTimeout())
            .maxJobsActive(jobConfig.maxJobsActive());

        if (!jobConfig.tenantIds().isEmpty()) {
            builder.tenantIds(jobConfig.tenantIds());
        }

        JobWorker jobWorker = builder.open();
//        LOGGER.info(". Starting Zeebe worker: {}", jobConfig);
        return jobWorker;
    }
}
