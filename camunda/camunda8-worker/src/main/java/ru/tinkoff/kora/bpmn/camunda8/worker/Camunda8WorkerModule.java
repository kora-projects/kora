package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.GrpcClientChannelFactory;
import ru.tinkoff.grpc.client.GrpcClientModule;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.json.common.JsonCommonModule;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

public interface Camunda8WorkerModule extends JsonCommonModule, GrpcClientModule {

    default Camunda8WorkerConfig camunda8WorkerConfig(Config config, ConfigValueExtractor<Camunda8WorkerConfig> extractor) {
        return extractor.extract(config.get("camunda8.worker"));
    }

    default Camunda8ClientConfig camunda8ClientConfig(Config config, ConfigValueExtractor<Camunda8ClientConfig> extractor) {
        return extractor.extract(config.get("camunda8.client"));
    }

    @DefaultComponent
    default ZeebeClientConfiguration camunda8ClientConfiguration(Camunda8ClientConfig clientConfig,
                                                                 Camunda8WorkerConfig workerConfig,
                                                                 @Nullable CredentialsProvider credentialsProvider,
                                                                 @Nullable JsonMapper jsonMapper,
                                                                 @Nullable ScheduledExecutorService jobWorkerExecutor) {
        final Camunda8WorkerConfig.JobConfig defaultJobConfig = workerConfig.getJobConfig(Camunda8WorkerConfig.DEFAULT);

        ZeebeClientBuilderImpl zeebeClientConfiguration = (ZeebeClientBuilderImpl) new ZeebeClientBuilderImpl()
            .defaultJobPollInterval(defaultJobConfig.pollInterval())
            .defaultJobTimeout(defaultJobConfig.timeout())
            .defaultJobWorkerMaxJobsActive(defaultJobConfig.maxJobsActive())
            .defaultJobWorkerStreamEnabled(defaultJobConfig.streamEnabled())
            .numJobWorkerExecutionThreads(clientConfig.executionThreads())
            .caCertificatePath(clientConfig.certificatePath())
            .defaultMessageTimeToLive(clientConfig.ttl())
            .keepAlive(clientConfig.keepAlive())
            .maxMessageSize(clientConfig.maxMessageSize())
            .grpcAddress(URI.create(clientConfig.grpc().url()))
            .applyEnvironmentVariableOverrides(false);

        if (credentialsProvider != null) {
            zeebeClientConfiguration.credentialsProvider(credentialsProvider);
        }
        if (jsonMapper != null) {
            zeebeClientConfiguration.withJsonMapper(jsonMapper);
        }
        if (jobWorkerExecutor != null) {
            zeebeClientConfiguration.jobWorkerExecutor(jobWorkerExecutor);
        }
        if (!defaultJobConfig.tenantIds().isEmpty()) {
            zeebeClientConfiguration.defaultJobWorkerTenantIds(defaultJobConfig.tenantIds());
        }
        if (!clientConfig.tls()) {
            zeebeClientConfiguration.usePlaintext();
        }
        if (clientConfig.rest() != null) {
            zeebeClientConfiguration
                .restAddress(URI.create(clientConfig.rest().url()))
                .preferRestOverGrpc(true);
        }

        return zeebeClientConfiguration;
    }

    @DefaultComponent
    default ZeebeBackoffFactory camunda8BackoffFactory() {
        return (config) -> BackoffSupplier.newBackoffBuilder()
            .maxDelay(config.maxDelay().toMillis())
            .minDelay(config.minDelay().toMillis())
            .backoffFactor(config.factory())
            .jitterFactor(config.jitter())
            .build();
    }

    @DefaultComponent
    default Camunda8WorkerTelemetryFactory camunda8WorkerTelemetryFactory(@Nullable Camunda8WorkerLoggerFactory loggerFactory,
                                                                          @Nullable Camunda8WorkerMetricsFactory metricsFactory) {
        return new DefaultCamunda8WorkerTelemetryFactory(loggerFactory, metricsFactory);
    }

    @Tag(ZeebeClient.class)
    default Wrapped<ManagedChannel> camunda8GrpcManagedChannel(Camunda8ClientConfig clientConfig,
                                                               All<ClientInterceptor> interceptors,
                                                               GrpcClientTelemetryFactory clientTelemetryFactory,
                                                               GrpcClientChannelFactory clientChannelFactory) {
        return ZeebeManagedChannelFactory.build(clientConfig, interceptors, clientTelemetryFactory, clientChannelFactory);
    }

    default Wrapped<ZeebeClient> camunda8ZeebeClient(Camunda8ClientConfig clientConfig,
                                                     ZeebeClientConfiguration clientConfiguration,
                                                     @Tag(ZeebeClient.class) ManagedChannel managedChannel) {
        return new KoraZeebeClient(clientConfig, clientConfiguration, managedChannel);
    }

    default ReadinessProbe camunda8ZeebeClientReadinessProbe(ZeebeClient zeebeClient) {
        return new ZeebeClientReadinessProbe(zeebeClient);
    }

    @Root
    default ZeebeResourceDeployment camunda8ResourceDeployment(ZeebeClient zeebeClient,
                                                               Camunda8ClientConfig clientConfig) {
        return new ZeebeResourceDeployment(zeebeClient, clientConfig.deployment());
    }

    @Root
    default KoraJobHandlerLifecycle camunda8KoraJobHandlerLifecycle(ZeebeClient client,
                                                                    All<KoraJobWorker> jobWorkers,
                                                                    Camunda8ClientConfig clientConfig,
                                                                    Camunda8WorkerConfig workerConfig,
                                                                    ZeebeBackoffFactory camundaBackoffFactory,
                                                                    Camunda8WorkerTelemetryFactory telemetryFactory,
                                                                    @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        return new KoraJobHandlerLifecycle(client, jobWorkers, clientConfig, workerConfig, camundaBackoffFactory, telemetryFactory, zeebeMetricsFactory);
    }
}
