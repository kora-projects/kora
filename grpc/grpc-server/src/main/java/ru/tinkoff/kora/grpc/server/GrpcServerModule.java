package ru.tinkoff.kora.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.netty.channel.EventLoopGroup;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.WrappedRefreshListener;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.grpc.server.config.GrpcServerConfig;
import ru.tinkoff.kora.grpc.server.interceptors.ContextServerInterceptor;
import ru.tinkoff.kora.grpc.server.interceptors.CoroutineContextInjectInterceptor;
import ru.tinkoff.kora.grpc.server.interceptors.TelemetryInterceptor;
import ru.tinkoff.kora.grpc.server.telemetry.DefaultGrpcServerTelemetry;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTelemetry;
import ru.tinkoff.kora.grpc.server.telemetry.NoopGrpcServerTelemetry;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface GrpcServerModule extends NettyCommonModule {

    default GrpcServerConfig grpcServerConfig(Config config, ConfigValueExtractor<GrpcServerConfig> configValueExtractor) {
        return configValueExtractor.extract(config.get("grpcServer"));
    }

    @Root
    default GrpcNettyServer grpcNettyServer(ValueOf<NettyServerBuilder> serverBuilder,
                                            ValueOf<GrpcServerConfig> config) {
        return new GrpcNettyServer(serverBuilder, config);
    }

    @DefaultComponent
    default GrpcServerTelemetry defaultGrpcServerTelemetry(GrpcServerConfig config, @Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        if (!config.telemetry().metrics().enabled() && !config.telemetry().logging().enabled() && !config.telemetry().tracing().enabled()) {
            return NoopGrpcServerTelemetry.INSTANCE;
        }
        if (tracer == null || !config.telemetry().tracing().enabled()) {
            tracer = TracerProvider.noop().get("grpc-server");
        }
        if (meterRegistry == null || !config.telemetry().metrics().enabled()) {
            meterRegistry = new CompositeMeterRegistry();
        }
        return new DefaultGrpcServerTelemetry(config.telemetry(), tracer, meterRegistry);
    }

    default NettyServerBuilder grpcNettyServerBuilder(
        ValueOf<GrpcServerConfig> config,
        List<DynamicBindableService> services,
        List<DynamicServerInterceptor> interceptors,
        @Tag(WorkerLoopGroup.class) EventLoopGroup eventLoop,
        @Tag(BossLoopGroup.class) EventLoopGroup bossEventLoop,
        NettyChannelFactory nettyChannelFactory,
        @Nullable
        GrpcServerBuilderConfigurer configurer,
        ValueOf<GrpcServerTelemetry> telemetry) {

        GrpcServerConfig grpcServerConfig = config.get();

        var builder = NettyServerBuilder.forPort(grpcServerConfig.port())
            .directExecutor()
            .addTransportFilter(VirtualThreadExecutorTransportFilter.INSTANCE)
            .callExecutor(VirtualThreadExecutorTransportFilter.INSTANCE)
            .maxInboundMessageSize(((int) grpcServerConfig.maxMessageSize().toBytes()))
            .bossEventLoopGroup(bossEventLoop)
            .workerEventLoopGroup(eventLoop)
            .channelFactory(nettyChannelFactory.getServerFactory());

        if (grpcServerConfig.maxConnectionAge() != null) {
            builder.maxConnectionAge(grpcServerConfig.maxConnectionAge().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (grpcServerConfig.maxConnectionAgeGrace() != null) {
            builder.maxConnectionAgeGrace(grpcServerConfig.maxConnectionAgeGrace().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (grpcServerConfig.keepAliveTime() != null) {
            builder.keepAliveTime(grpcServerConfig.keepAliveTime().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (grpcServerConfig.keepAliveTimeout() != null) {
            builder.keepAliveTimeout(grpcServerConfig.keepAliveTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (grpcServerConfig.reflectionEnabled() && isClassPresent("io.grpc.protobuf.services.ProtoReflectionService")) {
            builder.addService(ProtoReflectionService.newInstance());
        }

        interceptors.forEach(builder::intercept);
        builder
            .intercept(new TelemetryInterceptor(telemetry))
            .intercept(new ContextServerInterceptor())
            .intercept(CoroutineContextInjectInterceptor.newInstance());

        services.forEach(builder::addService);

        if (configurer != null) {
            builder = configurer.configure(builder);
        }

        return builder;
    }

    default WrappedRefreshListener<List<DynamicBindableService>> dynamicBindableServicesListener(All<ValueOf<BindableService>> services) {
        var dynamicServices = services.stream().map(DynamicBindableService::new).toList();

        return new WrappedRefreshListener<>() {
            @Override
            public void graphRefreshed() {
                dynamicServices.forEach(DynamicBindableService::graphRefreshed);
            }

            @Override
            public List<DynamicBindableService> value() {
                return dynamicServices;
            }
        };
    }

    default WrappedRefreshListener<List<DynamicServerInterceptor>> dynamicInterceptorsListener(All<ValueOf<ServerInterceptor>> interceptors) {
        var dynamicServerInterceptors = interceptors.stream().map(DynamicServerInterceptor::new).toList();

        return new WrappedRefreshListener<>() {
            @Override
            public void graphRefreshed() {
                dynamicServerInterceptors.forEach(DynamicServerInterceptor::graphRefreshed);
            }

            @Override
            public List<DynamicServerInterceptor> value() {
                return dynamicServerInterceptors;
            }
        };
    }

    private static boolean isClassPresent(String className) {
        try {
            return GrpcServerModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
