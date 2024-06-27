package ru.tinkoff.kora.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.netty.channel.EventLoopGroup;
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
import ru.tinkoff.kora.grpc.server.telemetry.*;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import java.util.List;

public interface GrpcServerModule extends NettyCommonModule {

    default GrpcServerConfig grpcServerConfig(Config config, ConfigValueExtractor<GrpcServerConfig> configValueExtractor) {
        return configValueExtractor.extract(config.get("grpcServer"));
    }

    @Root
    default GrpcNettyServer grpcNettyServer(ValueOf<NettyServerBuilder> serverBuilder) {
        return new GrpcNettyServer(serverBuilder);
    }

    @DefaultComponent
    default DefaultGrpcServerTelemetry defaultGrpcServerTelemetry(GrpcServerConfig config, @Nullable GrpcServerLogger logger, @Nullable GrpcServerMetricsFactory metrics, @Nullable GrpcServerTracer tracing) {
        return new DefaultGrpcServerTelemetry(config.telemetry(), metrics, tracing, logger);
    }

    @DefaultComponent
    default Slf4jGrpcServerLogger slf4jGrpcServerLogger() {
        return new Slf4jGrpcServerLogger();
    }

    default NettyServerBuilder grpcNettyServerBuilder(
        ValueOf<GrpcServerConfig> config,
        List<DynamicBindableService> services,
        List<DynamicServerInterceptor> interceptors,
        @Tag(WorkerLoopGroup.class) EventLoopGroup eventLoop,
        @Tag(BossLoopGroup.class) EventLoopGroup bossEventLoop,
        NettyChannelFactory nettyChannelFactory,
        ValueOf<GrpcServerTelemetry> telemetry) {

        var builder = NettyServerBuilder.forPort(config.get().port())
            .bossEventLoopGroup(bossEventLoop)
            .workerEventLoopGroup(eventLoop)
            .channelFactory(nettyChannelFactory.getServerFactory())
            .intercept(CoroutineContextInjectInterceptor.newInstance())
            .intercept(new ContextServerInterceptor())
            .intercept(new TelemetryInterceptor(telemetry));

        if(config.get().reflectionEnabled() && isClassPresent("io.grpc.protobuf.services.ProtoReflectionService")) {
            builder.addService(ProtoReflectionService.newInstance());
        }

        services.forEach(builder::addService);
        interceptors.forEach(builder::intercept);

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
