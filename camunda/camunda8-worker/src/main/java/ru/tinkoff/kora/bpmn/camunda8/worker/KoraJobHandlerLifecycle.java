package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.bpmn.camunda8.worker.Camunda8WorkerConfig.JobConfig;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetryFactory;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.ZeebeClientWorkerMetricsFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KoraJobHandlerLifecycle implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraJobHandlerLifecycle.class);

    private final ZeebeClient client;
    private final List<KoraJobWorker> jobWorkers;
    private final Camunda8ClientConfig clientConfig;
    private final Camunda8WorkerConfig workerConfig;
    private final ZeebeBackoffFactory zeebeBackoffFactory;
    private final Camunda8WorkerTelemetryFactory telemetryFactory;
    private final ZeebeClientWorkerMetricsFactory zeebeMetricsFactory;

    private final List<JobWorker> workers = new CopyOnWriteArrayList<>();

    public KoraJobHandlerLifecycle(ZeebeClient client,
                                   List<KoraJobWorker> jobWorkers,
                                   Camunda8ClientConfig clientConfig,
                                   Camunda8WorkerConfig workerConfig,
                                   ZeebeBackoffFactory zeebeBackoffFactory,
                                   Camunda8WorkerTelemetryFactory telemetryFactory,
                                   @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        this.client = client;
        this.jobWorkers = jobWorkers;
        this.clientConfig = clientConfig;
        this.workerConfig = workerConfig;
        this.zeebeBackoffFactory = zeebeBackoffFactory;
        this.telemetryFactory = telemetryFactory;
        this.zeebeMetricsFactory = zeebeMetricsFactory;
    }

    @Override
    public void init() {
        if (!jobWorkers.isEmpty()) {
            logger.debug("Camunda8 JobWorkers starting...");
            final long started = System.nanoTime();

            CompletableFuture[] jobOpeners = jobWorkers.stream()
                .map(koraJobWorker -> CompletableFuture.runAsync(() -> {
                    JobConfig jobConfig = workerConfig.getJobConfig(koraJobWorker.type());
                    if (jobConfig.enabled()) {
                        JobWorker jobWorker = createJobWorker(koraJobWorker, jobConfig);
                        workers.add(jobWorker);
                    }
                }))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(jobOpeners).join();

            final List<String> workerNames = jobWorkers.stream().map(KoraJobWorker::type).toList();
            logger.info("Camunda8 JobWorkers {} started in {}", workerNames, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    @Override
    public void release() {
        if (!workers.isEmpty()) {
            logger.debug("Camunda8 JobWorkers stopping...");
            final long started = System.nanoTime();

            for (JobWorker worker : workers) {
                try {
                    worker.close();
                } catch (Exception e) {
                    // ignore
                }
            }

            final List<String> workerNames = jobWorkers.stream().map(KoraJobWorker::type).toList();
            logger.info("Camunda8 JobWorkers {} stopped in {}", workerNames, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    public JobWorker createJobWorker(KoraJobWorker worker, JobConfig jobConfig) {
        final Camunda8WorkerTelemetry telemetry = telemetryFactory.get(worker.type(), clientConfig.telemetry());
        final JobHandler jobHandler = new WrappedJobHandler(telemetry, worker);
        final BackoffSupplier backoffSupplier = zeebeBackoffFactory.build(jobConfig.backoff());

        final JobWorkerMetrics jobWorkerMetrics = zeebeMetricsFactory == null
            ? null
            : zeebeMetricsFactory.get(worker.type(), clientConfig.telemetry().metrics());

        var builder = client.newWorker()
            .jobType(worker.type())
            .handler(jobHandler)
            .name(jobConfig.name())
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

        return builder.open();
    }
}
