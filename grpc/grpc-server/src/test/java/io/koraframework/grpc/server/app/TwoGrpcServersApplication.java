package io.koraframework.grpc.server.app;

import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapperModule;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.grpc.server.GrpcServerFactoryModule;
import io.koraframework.grpc.server.GrpcServerModule;

import java.util.Map;

@KoraApp
public interface TwoGrpcServersApplication extends ConfigValueMapperModule, GrpcServerModule {

    final class Admin {}

    default Config config() {
        return ConfigMappingUtils.fromMap(Map.of(
            "grpcServer", Map.of("port", 0),
            "grpcAdmin", Map.of("port", 0)));
    }

    @Tag(Admin.class)
    @FactoryModule
    default GrpcServerFactoryModule adminGrpcServer() {
        return new GrpcServerFactoryModule("admin-grpc", "grpcAdmin");
    }
}
