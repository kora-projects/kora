package io.koraframework.camunda.zeebe.worker;

import io.grpc.ChannelCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ServiceDescriptor;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.grpc.client.GrpcClientChannelFactory;
import io.koraframework.grpc.client.config.GrpcClientConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryInterceptor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;

final class ZeebeManagedChannelLifecycle implements Lifecycle, Wrapped<ManagedChannel> {

    private static final Logger logger = LoggerFactory.getLogger(ZeebeManagedChannelLifecycle.class);

    private final GrpcClientConfig config;
    private final ServiceDescriptor serviceDefinition;
    private final GrpcClientChannelFactory channelFactory;
    private final ChannelCredentials channelCredentials;
    private final GrpcClientTelemetryFactory telemetryFactory;
    private final Iterable<ClientInterceptor> interceptors;

    private volatile ManagedChannel channel;

    public ZeebeManagedChannelLifecycle(GrpcClientConfig config,
                                        @Nullable ChannelCredentials channelCredentials,
                                        Iterable<ClientInterceptor> interceptors,
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
        logger.debug("Zeebe GrpcManagedChannel starting...");
        final long started = TimeUtils.started();

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
        this.interceptors.forEach(interceptors::add);
        builder.intercept(interceptors);
        this.channel = builder.build();

        logger.info("Zeebe GrpcManagedChannel started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (channel != null) {
            logger.debug("Zeebe GrpcManagedChannel closing...");
            final long started = TimeUtils.started();

            channel.shutdown();
            this.channel = null;

            logger.info("Zeebe GrpcManagedChannel started in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public ManagedChannel value() {
        return this.channel;
    }
}
