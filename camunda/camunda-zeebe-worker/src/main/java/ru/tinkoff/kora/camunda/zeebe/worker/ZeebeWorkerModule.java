package ru.tinkoff.kora.camunda.zeebe.worker;

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
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.json.common.JsonCommonModule;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

public interface ZeebeWorkerModule extends JsonCommonModule, GrpcClientModule {

    default ZeebeWorkerConfig zeebeWorkerConfig(Config config, ConfigValueExtractor<ZeebeWorkerConfig> extractor) {
        return extractor.extract(config.get("zeebe.worker"));
    }

    default ZeebeClientConfig zeebeClientConfig(Config config, ConfigValueExtractor<ZeebeClientConfig> extractor) {
        return extractor.extract(config.get("zeebe.client"));
    }

    @DefaultComponent
    default ZeebeClientConfiguration zeebeWorkerClientConfiguration(ZeebeClientConfig clientConfig,
                                                                    ZeebeWorkerConfig workerConfig,
                                                                    @Nullable CredentialsProvider credentialsProvider,
                                                                    @Nullable JsonMapper jsonMapper,
                                                                    @Nullable ScheduledExecutorService jobWorkerExecutor) {
        final ZeebeWorkerConfig.JobConfig defaultJobConfig = workerConfig.getJobConfig(ZeebeWorkerConfig.DEFAULT);

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
    default ZeebeBackoffFactory zeebeWorkerBackoffFactory() {
        return (config) -> BackoffSupplier.newBackoffBuilder()
            .maxDelay(config.maxDelay().toMillis())
            .minDelay(config.minDelay().toMillis())
            .backoffFactor(config.factory())
            .jitterFactor(config.jitter())
            .build();
    }

    @DefaultComponent
    default ZeebeWorkerLoggerFactory zeebeWorkerLoggerFactory() {
        return new DefaultZeebeWorkerLoggerFactory();
    }

    @DefaultComponent
    default ZeebeWorkerTelemetryFactory zeebeWorkerTelemetryFactory(@Nullable ZeebeWorkerLoggerFactory loggerFactory,
                                                                    @Nullable ZeebeWorkerMetricsFactory metricsFactory,
                                                                    @Nullable ZeebeWorkerTracerFactory tracerFactory) {
        return new DefaultZeebeWorkerTelemetryFactory(loggerFactory, metricsFactory, tracerFactory);
    }

    @Tag(ZeebeClient.class)
    default Wrapped<ManagedChannel> zeebeWorkerGrpcManagedChannel(ZeebeClientConfig clientConfig,
                                                                  All<ClientInterceptor> interceptors,
                                                                  GrpcClientTelemetryFactory clientTelemetryFactory,
                                                                  GrpcClientChannelFactory clientChannelFactory) {
        return ZeebeManagedChannelFactory.build(clientConfig, interceptors, clientTelemetryFactory, clientChannelFactory);
    }

    default Wrapped<ZeebeClient> zeebeWorkerClient(ZeebeClientConfig clientConfig,
                                                   ZeebeClientConfiguration clientConfiguration,
                                                   @Tag(ZeebeClient.class) ManagedChannel managedChannel) {
        return new KoraZeebeClient(clientConfig, clientConfiguration, managedChannel);
    }

    default ReadinessProbe zeebeWorkerClientReadinessProbe(ZeebeClient zeebeClient) {
        return new ZeebeClientReadinessProbe(zeebeClient);
    }

    @Root
    default ZeebeResourceDeployment zeebeWorkerResourceDeployment(ZeebeClient zeebeClient,
                                                                  ZeebeClientConfig clientConfig) {
        return new ZeebeResourceDeployment(zeebeClient, clientConfig.deployment());
    }

    @Root
    default KoraJobHandlerLifecycle zeebeWorkerJobHandlerLifecycle(ZeebeClient client,
                                                                   All<KoraJobWorker> jobWorkers,
                                                                   ZeebeClientConfig clientConfig,
                                                                   ZeebeWorkerConfig workerConfig,
                                                                   ZeebeBackoffFactory camundaBackoffFactory,
                                                                   ZeebeWorkerTelemetryFactory telemetryFactory,
                                                                   @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        return new KoraJobHandlerLifecycle(client, jobWorkers, clientConfig, workerConfig, camundaBackoffFactory, telemetryFactory, zeebeMetricsFactory);
    }
}
