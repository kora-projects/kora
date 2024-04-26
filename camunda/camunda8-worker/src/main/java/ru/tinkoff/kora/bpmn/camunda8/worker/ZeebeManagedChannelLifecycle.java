package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.grpc.ChannelCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ServiceDescriptor;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.GrpcClientChannelFactory;
import ru.tinkoff.grpc.client.config.GrpcClientConfig;
import ru.tinkoff.grpc.client.config.GrpcClientConfigInterceptor;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryFactory;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryInterceptor;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ZeebeManagedChannelLifecycle implements Lifecycle, Wrapped<ManagedChannel> {
    private final GrpcClientConfig config;
    private final ServiceDescriptor serviceDefinition;
    private final GrpcClientChannelFactory channelFactory;
    private final ChannelCredentials channelCredentials;
    private final GrpcClientTelemetryFactory telemetryFactory;
    private final List<ClientInterceptor> interceptors;

    private volatile ManagedChannel channel;

    public ZeebeManagedChannelLifecycle(GrpcClientConfig config,
                                        @Nullable ChannelCredentials channelCredentials,
                                        List<ClientInterceptor> interceptors,
                                        GrpcClientTelemetryFactory telemetryFactory,
                                        GrpcClientChannelFactory channelFactory,
                                        ServiceDescriptor serviceDefinition) {
        this.config = config;
        this.serviceDefinition = serviceDefinition;
        this.channelCredentials = channelCredentials;
        this.channelFactory = channelFactory;
        this.telemetryFactory = telemetryFactory;
        this.interceptors = interceptors;
    }

    @Override
    public void init() {
        var uri = URI.create(this.config.url());
        var host = uri.getHost();
        var port = uri.getPort();
        var scheme = uri.getScheme();
        if (port < 0) {
            if (Objects.equals(scheme, "http")) {
                port = 80;
            } else if (Objects.equals(scheme, "https")) {
                port = 443;
            } else {
                throw new IllegalArgumentException("Unknown scheme '" + scheme + "'");
            }
        }
        var builder = this.channelCredentials == null
            ? this.channelFactory.forAddress(host, port)
            : this.channelFactory.forAddress(host, port, this.channelCredentials);
        if (Objects.equals(scheme, "http")) {
            builder.usePlaintext();
        }

        var interceptors = new ArrayList<ClientInterceptor>(2);
        var telemetry = telemetryFactory.get(serviceDefinition, config.telemetry(), uri);
        if (telemetry != null) {
            interceptors.add(new GrpcClientTelemetryInterceptor(telemetry));
        }
        interceptors.addAll(this.interceptors);
        builder.intercept(interceptors);
        this.channel = builder.build();
    }

    @Override
    public void release() {
        if (channel != null) {
            channel.shutdown();
            this.channel = null;
        }
    }

    @Override
    public ManagedChannel value() {
        return this.channel;
    }
}
