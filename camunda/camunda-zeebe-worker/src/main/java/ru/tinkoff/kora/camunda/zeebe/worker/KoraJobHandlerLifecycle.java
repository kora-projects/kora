package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.camunda.zeebe.worker.ZeebeWorkerConfig.JobConfig;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class KoraJobHandlerLifecycle implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraJobHandlerLifecycle.class);

    private final ZeebeClient client;
    private final List<KoraJobWorker> jobWorkers;
    private final ZeebeClientConfig clientConfig;
    private final ZeebeWorkerConfig workerConfig;
    private final ZeebeBackoffFactory zeebeBackoffFactory;
    private final ZeebeWorkerTelemetryFactory telemetryFactory;
    private final ZeebeClientWorkerMetricsFactory zeebeMetricsFactory;

    private final List<JobWorker> workers = new CopyOnWriteArrayList<>();

    public KoraJobHandlerLifecycle(ZeebeClient client,
                                   List<KoraJobWorker> jobWorkers,
                                   ZeebeClientConfig clientConfig,
                                   ZeebeWorkerConfig workerConfig,
                                   ZeebeBackoffFactory zeebeBackoffFactory,
                                   ZeebeWorkerTelemetryFactory telemetryFactory,
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
            logger.debug("Zeebe JobWorkers starting...");
            final long started = System.nanoTime();

            var workersByType = jobWorkers.stream().collect(Collectors.groupingBy(KoraJobWorker::type));
            for (List<KoraJobWorker> value : workersByType.values()) {
                if (value.size() > 1) {
                    logger.warn("Found '{}' Zeebe JobWorkers with same JobType: {}", value.size(), value.get(0).type());
                }
            }

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
            logger.info("Zeebe JobWorkers {} started in {}", workerNames, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    @Override
    public void release() {
        if (!workers.isEmpty()) {
            logger.debug("Zeebe JobWorkers stopping...");
            final long started = System.nanoTime();

            for (JobWorker worker : workers) {
                try {
                    worker.close();
                } catch (Exception e) {
                    // ignore
                }
            }

            final List<String> workerNames = jobWorkers.stream().map(KoraJobWorker::type).toList();
            logger.info("Zeebe JobWorkers {} stopped in {}", workerNames, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    public JobWorker createJobWorker(KoraJobWorker worker, JobConfig jobConfig) {
        final ZeebeWorkerTelemetry telemetry = telemetryFactory.get(worker.type(), clientConfig.telemetry());
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
