package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.impl.util.VersionUtil;
import io.grpc.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.GrpcClientChannelFactory;
import ru.tinkoff.grpc.client.config.GrpcClientConfig;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ZeebeManagedChannelFactory {

    private ZeebeManagedChannelFactory() {}

    static Wrapped<ManagedChannel> build(Camunda8ClientConfig clientConfig,
                                         All<ClientInterceptor> interceptors,
                                         GrpcClientTelemetryFactory clientTelemetryFactory,
                                         GrpcClientChannelFactory clientChannelFactory) {
        final GrpcClientConfig grpcClientConfig = getZeebeGrpcConfig(clientConfig);

        final GrpcClientChannelFactory grpcClientChannelFactory = getZeebeClientChannelFactory(clientChannelFactory, clientConfig);

        final String serviceName = ZeebeClient.class.getCanonicalName();
        final ServiceDescriptor descriptor = new ServiceDescriptor(serviceName);
        return new ZeebeManagedChannelLifecycle(grpcClientConfig, null, interceptors, clientTelemetryFactory, grpcClientChannelFactory, descriptor);
    }

    private static GrpcClientConfig getZeebeGrpcConfig(Camunda8ClientConfig clientConfig) {
        return new GrpcClientConfig() {
            @Override
            public String url() {
                return clientConfig.grpc().url();
            }

            @Nullable
            @Override
            public Duration timeout() {
                return null;
            }

            @Override
            public TelemetryConfig telemetry() {
                return clientConfig.telemetry();
            }
        };
    }

    private static GrpcClientChannelFactory getZeebeClientChannelFactory(GrpcClientChannelFactory factory, Camunda8ClientConfig clientConfig) {
        return new GrpcClientChannelFactory() {
            @Override
            public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress) {
                return configure(factory.forAddress(serverAddress));
            }

            @Override
            public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds) {
                return configure(factory.forAddress(serverAddress, creds));
            }

            @Override
            public ManagedChannelBuilder<?> forTarget(String target) {
                return configure(factory.forTarget(target));
            }

            @Override
            public ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds) {
                return configure(factory.forTarget(target, creds));
            }

            private ManagedChannelBuilder<?> configure(ManagedChannelBuilder<?> builder) {
                builder.keepAliveTime(clientConfig.keepAlive().toMillis(), TimeUnit.MILLISECONDS);
                builder.userAgent("zeebe-client-java/" + VersionUtil.getVersion());
                builder.maxInboundMessageSize(clientConfig.maxMessageSize());
                if (clientConfig.grpc().retryPolicy().enabled()) {
                    Map<String, Object> serviceConfig = getServiceConfig(clientConfig);
                    builder.defaultServiceConfig(serviceConfig);
                    builder.enableRetry();
                }

                return builder;
            }
        };
    }

    private static Map<String, Object> getServiceConfig(Camunda8ClientConfig clientConfig) {
        var services1 = getServices("ActivateJobs", "CancelProcessInstance", "CompleteJob", "DeleteResource",
            "EvaluateDecision", "FailJob", "ResolveIncident", "SetVariables", "StreamActivatedJobs", "Topology", "MigrateProcessInstance");

        var services2 = getServices("BroadcastSignal", "CreateProcessInstance", "CreateProcessInstanceWithResult", "DeployProcess",
            "DeployResource", "ModifyProcessInstance", "PublishMessage", "ThrowError", "UpdateJobRetries", "UpdateJobTimeout");

        return Map.of(
            "methodConfig", List.of(
                Map.of(
                    "name", services1,
                    "waitForReady", true,
                    "retryPolicy", Map.of(
                        "maxAttempts", (double) clientConfig.grpc().retryPolicy().attempts(),
                        "initialBackoff", ((double) clientConfig.grpc().retryPolicy().delay().toMillis() / 1000) + "s",
                        "maxBackoff", ((double) clientConfig.grpc().retryPolicy().delayMax().toMillis() / 1000) + "s",
                        "backoffMultiplier", clientConfig.grpc().retryPolicy().step(),
                        "retryableStatusCodes", List.of("UNAVAILABLE", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED")
                    )
                ),
                Map.of(
                    "name", services2,
                    "waitForReady", true,
                    "retryPolicy", Map.of(
                        "maxAttempts", (double) clientConfig.grpc().retryPolicy().attempts(),
                        "initialBackoff", ((double) clientConfig.grpc().retryPolicy().delay().toMillis() / 1000) + "s",
                        "maxBackoff", ((double) clientConfig.grpc().retryPolicy().delayMax().toMillis() / 1000) + "s",
                        "backoffMultiplier", clientConfig.grpc().retryPolicy().step(),
                        "retryableStatusCodes", List.of("UNAVAILABLE", "RESOURCE_EXHAUSTED")
                    )
                )
            ),
            "healthCheckConfig", Map.of("serviceName", "gateway_protocol.Gateway")
        );
    }

    private static List<Map<String, String>> getServices(String... services) {
        final List<Map<String, String>> serviceList = new ArrayList<>();

        for (String service : services) {
            serviceList.add(Map.of("service", "gateway_protocol.Gateway", "method", service));
        }

        return serviceList;
    }
}
