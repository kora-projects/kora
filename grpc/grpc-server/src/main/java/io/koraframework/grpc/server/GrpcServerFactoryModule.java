package io.koraframework.grpc.server;

import io.grpc.*;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.WrappedRefreshListener;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.grpc.server.handler.DynamicBindableService;
import io.koraframework.grpc.server.handler.VirtualThreadExecutorTransportFilter;
import io.koraframework.grpc.server.interceptor.DynamicServerInterceptor;
import io.koraframework.grpc.server.interceptor.TelemetryInterceptor;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryFactory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GrpcServerFactoryModule {

    private final String name;
    private final String configPath;

    public GrpcServerFactoryModule(String name, String configPath) {
        this.name = name;
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public GrpcServerConfig grpcServerConfig(Config config, ConfigValueMapper<GrpcServerConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Root
    @Tag(Tag.Factory.class)
    public GrpcServer grpcServer(@Tag(Tag.Factory.class) ValueOf<ForwardingServerBuilder<?>> serverBuilder,
                                 @Tag(Tag.Factory.class) ValueOf<GrpcServerConfig> config) {
        return new GrpcServer(serverBuilder, config);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public ForwardingServerBuilder<?> grpcServerBuilder(@Tag(Tag.Factory.class) ValueOf<GrpcServerConfig> config,
                                                        @Tag(Tag.Factory.class) List<DynamicBindableService> services,
                                                        @Tag(Tag.Factory.class) List<DynamicServerInterceptor> interceptors,
                                                        @Tag(Tag.Factory.class) @Nullable ServerCredentials serverCredentials,
                                                        @Tag(Tag.Factory.class) @Nullable Configurer<ForwardingServerBuilder<?>> configurer,
                                                        @Tag(Tag.Factory.class) GrpcServerTelemetryFactory telemetryFactory) {
        if (serverCredentials == null) {
            serverCredentials = InsecureServerCredentials.create();
        }
        GrpcServerConfig grpcServerConfig = config.get();
        var builder = OkHttpServerBuilder.forPort(grpcServerConfig.port(), serverCredentials)
            .directExecutor()
            .addTransportFilter(VirtualThreadExecutorTransportFilter.INSTANCE)
            .callExecutor(VirtualThreadExecutorTransportFilter.INSTANCE)
            .maxInboundMessageSize(((int) grpcServerConfig.maxMessageSize().toBytes()));

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
        if (grpcServerConfig.reflectionEnabled() && isClassPresent("io.grpc.protobuf.services.ProtoReflectionServiceV1")) {
            builder.addService(ProtoReflectionServiceV1.newInstance());
        }

        interceptors.forEach(builder::intercept);
        builder.intercept(new TelemetryInterceptor(telemetryFactory.get(this.name, grpcServerConfig.port(), grpcServerConfig.telemetry())));

        services.forEach(builder::addService);

        if (configurer != null) {
            return configurer.configure(builder);
        }

        return builder;
    }

    @Tag(Tag.Factory.class)
    public WrappedRefreshListener<List<DynamicBindableService>> dynamicBindableServicesListener(@Tag(Tag.Factory.class) All<ValueOf<BindableService>> services) {
        var dynamicServices = new ArrayList<DynamicBindableService>();
        // caching All<ValueOf<T>> won't be safe after conditional components introduced, but let's just hope conditional grpc service instance is not a thing
        for (var service : services) {
            dynamicServices.add(new DynamicBindableService(service));
        }

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

    @Tag(Tag.Factory.class)
    public WrappedRefreshListener<List<DynamicServerInterceptor>> dynamicInterceptorsListener(@Tag(Tag.Factory.class) All<ValueOf<ServerInterceptor>> interceptors) {
        var dynamicServerInterceptors = new ArrayList<DynamicServerInterceptor>();
        // caching All<ValueOf<T>> won't be safe after conditional components introduced, but let's just hope conditional grpc service interceptor is not a thing
        for (var interceptor : interceptors) {
            dynamicServerInterceptors.add(new DynamicServerInterceptor(interceptor));
        }

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
            return GrpcServerFactoryModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
