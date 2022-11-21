package ru.tinkoff.kora.grpc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.WrappedRefreshListener;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.grpc.config.GrpcServerConfig;
import ru.tinkoff.kora.grpc.interceptors.ContextServerInterceptor;
import ru.tinkoff.kora.grpc.interceptors.CoroutineContextInjectInterceptor;
import ru.tinkoff.kora.grpc.interceptors.TelemetryInterceptor;
import ru.tinkoff.kora.grpc.telemetry.*;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface GrpcModule extends NettyCommonModule {
    default GrpcServerConfig grpcServerConfig(Config config, ConfigValueExtractor<GrpcServerConfig> configValueExtractor) {
        if (config.hasPath("grpcServer")) {
            return configValueExtractor.extract(config.getValue("grpcServer"));
        } else {
            return configValueExtractor.extract(ConfigValueFactory.fromMap(Map.of()));
        }
    }

    default GrpcServer grpcServer(ValueOf<NettyServerBuilder> serverBuilder) {
        return new GrpcServer(serverBuilder);
    }

    @DefaultComponent
    default DefaultGrpcServerTelemetry defaultGrpcServerTelemetry(@Nullable GrpcServerLogger logger, @Nullable GrpcServerMetricsFactory metrics, @Nullable GrpcServerTracer tracing) {
        return new DefaultGrpcServerTelemetry(metrics, tracing, logger);
    }

    @DefaultComponent
    default Slf4jGrpcServerLogger slf4jGrpcServerLogger() {
        return new Slf4jGrpcServerLogger();
    }

    default NettyServerBuilder serverBuilder(
        ValueOf<GrpcServerConfig> config,
        List<DynamicBindableService> services,
        List<DynamicServerInterceptor> interceptors,
        EventLoopGroup eventLoop,
        @Tag(BossLoopGroup.class) EventLoopGroup bossEventLoop,
        GrpcServerTelemetry telemetry) {
        var builder = NettyServerBuilder.forPort(config.get().port())
            .bossEventLoopGroup(bossEventLoop)
            .workerEventLoopGroup(eventLoop)
            .channelType(NettyCommonModule.serverChannelType())
            .intercept(CoroutineContextInjectInterceptor.newInstance())
            .intercept(new ContextServerInterceptor())
            .intercept(new TelemetryInterceptor(telemetry));

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
}
