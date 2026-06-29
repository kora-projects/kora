package io.koraframework.camunda.zeebe.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryFactory;
import io.koraframework.camunda.zeebe.worker.telemetry.impl.DefaultZeebeWorkerLoggerFactory;
import io.koraframework.camunda.zeebe.worker.telemetry.impl.DefaultZeebeWorkerMetricsFactory;
import io.koraframework.camunda.zeebe.worker.telemetry.impl.DefaultZeebeWorkerTelemetryFactory;
import io.koraframework.camunda.zeebe.worker.telemetry.impl.MicrometerZeebeClientWorkerJobMetricsFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.grpc.client.GrpcClientModule;
import io.koraframework.grpc.client.channel.GrpcClientChannelFactory;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import io.koraframework.json.common.JsonModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

public interface ZeebeWorkerModule extends GrpcClientModule, JsonModule {

    default ZeebeWorkerConfig zeebeWorkerConfig(Config config, ConfigValueExtractor<ZeebeWorkerConfig> extractor) {
        return extractor.extractOrThrow(config.get("zeebe.worker"));
    }

    default ZeebeClientConfig zeebeClientConfig(Config config, ConfigValueExtractor<ZeebeClientConfig> extractor) {
        return extractor.extractOrThrow(config.get("zeebe.client"));
    }

    @DefaultComponent
    default CamundaClientConfiguration zeebeWorkerClientConfiguration(ZeebeClientConfig clientConfig,
                                                                      ZeebeWorkerConfig workerConfig,
                                                                      @Nullable CredentialsProvider credentialsProvider,
                                                                      @Nullable JsonMapper jsonMapper,
                                                                      @Nullable ScheduledExecutorService jobWorkerExecutor) {
        final ZeebeWorkerConfig.JobConfig defaultJobConfig = workerConfig.getJobConfig(ZeebeWorkerConfig.DEFAULT);

        CamundaClientBuilderImpl zeebeClientConfiguration = (CamundaClientBuilderImpl) new CamundaClientBuilderImpl()
            .defaultJobPollInterval(defaultJobConfig.pollInterval())
            .defaultJobTimeout(defaultJobConfig.timeout())
            .numJobWorkerExecutionThreads(clientConfig.executionThreads())
            .caCertificatePath(clientConfig.certificatePath())
            .keepAlive(clientConfig.keepAlive())
            .applyEnvironmentVariableOverrides(false);

        if (clientConfig.grpc() != null) {
            zeebeClientConfiguration = (CamundaClientBuilderImpl) zeebeClientConfiguration
                .grpcAddress(URI.create(clientConfig.grpc().url()))
                .defaultMessageTimeToLive(clientConfig.grpc().ttl())
                .maxMessageSize((int) clientConfig.grpc().maxMessageSize().toBytes())
                .preferRestOverGrpc(false);
        }

        if (defaultJobConfig.streamEnabled() != null) {
            zeebeClientConfiguration = (CamundaClientBuilderImpl) zeebeClientConfiguration.defaultJobWorkerStreamEnabled(defaultJobConfig.streamEnabled());
        }
        if (defaultJobConfig.maxJobsActive() != null) {
            zeebeClientConfiguration = (CamundaClientBuilderImpl) zeebeClientConfiguration.defaultJobWorkerMaxJobsActive(defaultJobConfig.maxJobsActive());
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
        zeebeClientConfiguration
            .restAddress(URI.create(clientConfig.rest().url()));

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
    default ZeebeWorkerTelemetryFactory zeebeWorkerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                                                    @Nullable Tracer tracer,
                                                                    @Nullable DefaultZeebeWorkerLoggerFactory loggerFactory,
                                                                    @Nullable DefaultZeebeWorkerMetricsFactory metricsFactory) {
        return new DefaultZeebeWorkerTelemetryFactory(meterRegistry, tracer, loggerFactory, metricsFactory);
    }

    @Tag(CamundaClient.class)
    @DefaultComponent
    default Wrapped<ManagedChannel> zeebeWorkerGrpcManagedChannel(ZeebeClientConfig clientConfig,
                                                                  All<ClientInterceptor> interceptors,
                                                                  GrpcClientTelemetryFactory clientTelemetryFactory,
                                                                  GrpcClientChannelFactory clientChannelFactory) {
        return ZeebeManagedChannelFactory.build(clientConfig, interceptors, clientTelemetryFactory, clientChannelFactory);
    }

    default Wrapped<CamundaClient> zeebeWorkerClient(ZeebeClientConfig clientConfig,
                                                     CamundaClientConfiguration clientConfiguration,
                                                     @Tag(CamundaClient.class) ManagedChannel managedChannel) {
        return new KoraZeebeClient(clientConfig, clientConfiguration, managedChannel);
    }

    @DefaultComponent
    default ZeebeClientWorkerMetricsFactory micrometerZeebeClientWorkerJobMetricsFactory(@Nullable MeterRegistry meterRegistry) {
        return new MicrometerZeebeClientWorkerJobMetricsFactory(meterRegistry);
    }

    @Root
    default ZeebeResourceDeployment zeebeWorkerResourceDeployment(CamundaClient zeebeClient,
                                                                  ZeebeClientConfig clientConfig) {
        return new ZeebeResourceDeployment(zeebeClient, clientConfig.deployment());
    }

    @Root
    default KoraZeebeJobWorkerEngine zeebeKoraZeebeJobWorkerEngine(CamundaClient client,
                                                                   All<KoraJobWorker> jobWorkers,
                                                                   ZeebeClientConfig clientConfig,
                                                                   ZeebeWorkerConfig workerConfig,
                                                                   ZeebeBackoffFactory camundaBackoffFactory,
                                                                   ZeebeWorkerTelemetryFactory telemetryFactory,
                                                                   @Nullable ZeebeClientWorkerMetricsFactory zeebeMetricsFactory) {
        return new KoraZeebeJobWorkerEngine(client, jobWorkers, clientConfig, workerConfig, camundaBackoffFactory, telemetryFactory, zeebeMetricsFactory);
    }
}
