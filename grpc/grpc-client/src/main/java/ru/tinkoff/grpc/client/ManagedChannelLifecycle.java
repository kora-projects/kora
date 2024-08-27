package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ServiceDescriptor;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.grpc.client.config.GrpcClientConfig;
import ru.tinkoff.grpc.client.config.GrpcClientConfigInterceptor;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryFactory;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryInterceptor;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;

public final class ManagedChannelLifecycle implements Lifecycle, Wrapped<ManagedChannel> {

    private static final Logger logger = LoggerFactory.getLogger(ManagedChannelLifecycle.class);

    private final GrpcClientConfig config;
    private final ServiceDescriptor serviceDefinition;
    private final GrpcClientChannelFactory channelFactory;
    private final ChannelCredentials channelCredentials;
    private final GrpcClientTelemetryFactory telemetryFactory;
    private final All<ClientInterceptor> interceptors;
    private volatile ManagedChannel channel;

    public ManagedChannelLifecycle(GrpcClientConfig config,
                                   @Nullable ChannelCredentials channelCredentials,
                                   All<ClientInterceptor> interceptors,
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
        logger.debug("GrpcManagedChannel '{}' starting...", this.config.url());
        var started = System.nanoTime();

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
        interceptors.addAll(this.interceptors);

        var telemetry = telemetryFactory.get(serviceDefinition, config.telemetry(), uri);
        if (telemetry != null) {
            interceptors.add(new GrpcClientTelemetryInterceptor(telemetry));
        }
        interceptors.add(new GrpcClientConfigInterceptor(this.config));
        builder.intercept(interceptors);

        this.channel = builder.build();
        logger.info("GrpcManagedChannel '{}' started in {}", this.config.url(), TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var channel = this.channel;
        this.channel = null;
        if (channel != null) {
            logger.debug("GrpcManagedChannel '{}' stopping...", this.config.url());
            var started = System.nanoTime();

            channel.shutdown();

            logger.info("GrpcManagedChannel '{}' stopped in {}", this.config.url(), TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public ManagedChannel value() {
        return this.channel;
    }
}
