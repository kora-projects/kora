package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.grpc.client.GrpcClientChannelFactory;
import ru.tinkoff.grpc.client.GrpcClientModule;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.DefaultZeebeWorkerTelemetryFactory;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.MicrometerZeebeClientWorkerJobMetricsFactory;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.json.common.JsonModule;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public interface ZeebeWorkerModule extends GrpcClientModule, JsonModule {

    default ZeebeWorkerConfig zeebeWorkerConfig(Config config, ConfigValueExtractor<ZeebeWorkerConfig> extractor) {
        return extractor.extract(config.get("zeebe.worker"));
    }

    default ZeebeClientConfig zeebeClientConfig(Config config, ConfigValueExtractor<ZeebeClientConfig> extractor) {
        return extractor.extract(config.get("zeebe.client"));
    }

    @DefaultComponent
    @SuppressWarnings("deprecation")
    default ZeebeClientConfiguration zeebeWorkerClientConfiguration(ZeebeClientConfig clientConfig,
                                                                    ZeebeWorkerConfig workerConfig,
                                                                    @Nullable CredentialsProvider credentialsProvider,
                                                                    @Nullable JsonMapper jsonMapper,
                                                                    @Nullable ScheduledExecutorService jobWorkerExecutor) {
        final ZeebeWorkerConfig.JobConfig defaultJobConfig = workerConfig.getJobConfig(ZeebeWorkerConfig.DEFAULT);

        ZeebeClientBuilderImpl zeebeClientConfiguration = (ZeebeClientBuilderImpl) new ZeebeClientBuilderImpl()
            .defaultJobPollInterval(defaultJobConfig.pollInterval())
            .defaultJobTimeout(defaultJobConfig.timeout())
            .numJobWorkerExecutionThreads(clientConfig.executionThreads())
            .caCertificatePath(clientConfig.certificatePath())
            .keepAlive(clientConfig.keepAlive())
            .defaultMessageTimeToLive(clientConfig.grpc().ttl())
            .maxMessageSize((int) clientConfig.grpc().maxMessageSize().toBytes())
            .grpcAddress(URI.create(clientConfig.grpc().url()))
            .applyEnvironmentVariableOverrides(false)
            .jobWorkerExecutor(Executors.newScheduledThreadPool(0, Thread.ofVirtual().name("zeebe-worker-", 1).factory()), true);

        if (defaultJobConfig.streamEnabled() != null) {
            zeebeClientConfiguration = (ZeebeClientBuilderImpl) zeebeClientConfiguration.defaultJobWorkerStreamEnabled(defaultJobConfig.streamEnabled());
        }
        if (defaultJobConfig.maxJobsActive() != null) {
            zeebeClientConfiguration = (ZeebeClientBuilderImpl) zeebeClientConfiguration.defaultJobWorkerMaxJobsActive(defaultJobConfig.maxJobsActive());
        }
        if (credentialsProvider != null) {
            zeebeClientConfiguration.credentialsProvider(credentialsProvider);
        }
        if (jsonMapper != null) {
            zeebeClientConfiguration.withJsonMapper(jsonMapper);
        }
        if (jobWorkerExecutor != null) {
            zeebeClientConfiguration.jobWorkerExecutor(jobWorkerExecutor);
        }
        if (defaultJobConfig.tenantIds() != null && !defaultJobConfig.tenantIds().isEmpty()) {
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
            .backoffFactor(config.factor())
            .jitterFactor(config.jitter())
            .build();
    }

    @DefaultComponent
    default ZeebeWorkerTelemetryFactory zeebeWorkerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultZeebeWorkerTelemetryFactory(meterRegistry, tracer);
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

    @DefaultComponent
    default MicrometerZeebeClientWorkerJobMetricsFactory micrometerZeebeClientWorkerJobMetricsFactory(@Nullable MeterRegistry meterRegistry) {
        return new MicrometerZeebeClientWorkerJobMetricsFactory(meterRegistry);
    }

    @Root
    default ZeebeResourceDeployment zeebeWorkerResourceDeployment(ZeebeClient zeebeClient,
                                                                  ZeebeClientConfig clientConfig) {
        return new ZeebeResourceDeployment(zeebeClient, clientConfig.deployment());
    }

    @Root
    default KoraZeebeJobWorkerEngine zeebeKoraZeebeJobWorkerEngine(ZeebeClient client,
                                                                   All<KoraJobWorker> jobWorkers,
                                                                   ZeebeClientConfig clientConfig,
                                                                   ZeebeWorkerConfig workerConfig,
                                                                   ZeebeBackoffFactory camundaBackoffFactory,
                                                                   ZeebeWorkerTelemetryFactory telemetryFactory,
                                                                   @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        return new KoraZeebeJobWorkerEngine(client, jobWorkers, clientConfig, workerConfig, camundaBackoffFactory, telemetryFactory, zeebeMetricsFactory);
    }
}
