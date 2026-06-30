package io.koraframework.grpc.server;

import io.grpc.*;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.WrappedRefreshListener;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.grpc.server.handler.DynamicBindableService;
import io.koraframework.grpc.server.handler.VirtualThreadExecutorTransportFilter;
import io.koraframework.grpc.server.interceptors.DynamicServerInterceptor;
import io.koraframework.grpc.server.interceptors.TelemetryInterceptor;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetry;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerLoggerFactory;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerMetricsFactory;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface GrpcServerModule {

    default GrpcServerConfig grpcServerConfig(Config config, ConfigValueExtractor<GrpcServerConfig> configValueExtractor) {
        return configValueExtractor.extract(config.get("grpcServer"));
    }

    @Root
    default GrpcServer grpcServer(ValueOf<ForwardingServerBuilder<?>> serverBuilder,
                                  ValueOf<GrpcServerConfig> config) {
        return new GrpcServer(serverBuilder, config);
    }

    @DefaultComponent
    default GrpcServerTelemetry defaultGrpcServerTelemetry(GrpcServerConfig config,
                                                           @Nullable Tracer tracer,
                                                           @Nullable MeterRegistry meterRegistry,
                                                           @Nullable DefaultGrpcServerLoggerFactory loggerFactory,
                                                           @Nullable DefaultGrpcServerMetricsFactory metricsFactory) {
        return new DefaultGrpcServerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory).get(config.telemetry());
    }

    @DefaultComponent
    default ForwardingServerBuilder<?> grpcServerBuilder(ValueOf<GrpcServerConfig> config,
                                                         List<DynamicBindableService> services,
                                                         List<DynamicServerInterceptor> interceptors,
                                                         @Nullable ServerCredentials serverCredentials,
                                                         @Nullable Configurer<ForwardingServerBuilder<?>> configurer,
                                                         ValueOf<GrpcServerTelemetry> telemetry) {
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
        builder.intercept(new TelemetryInterceptor(telemetry));

        services.forEach(builder::addService);

        if (configurer != null) {
            return configurer.configure(builder);
        }

        return builder;
    }

    default WrappedRefreshListener<List<DynamicBindableService>> dynamicBindableServicesListener(All<ValueOf<BindableService>> services) {
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

    default WrappedRefreshListener<List<DynamicServerInterceptor>> dynamicInterceptorsListener(All<ValueOf<ServerInterceptor>> interceptors) {
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
            return GrpcServerModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
