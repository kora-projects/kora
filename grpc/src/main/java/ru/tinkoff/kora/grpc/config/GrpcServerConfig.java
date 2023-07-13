package ru.tinkoff.kora.grpc.config;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface GrpcServerConfig {
    default int port() {
        return 8090;
    }
}
