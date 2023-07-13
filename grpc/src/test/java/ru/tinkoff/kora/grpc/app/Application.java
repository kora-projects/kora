package ru.tinkoff.kora.grpc.app;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.grpc.GrpcModule;
import ru.tinkoff.kora.grpc.config.$GrpcServerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.grpc.config.GrpcServerConfig;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@KoraApp
public interface Application extends GrpcModule {
    default Config config() {
        return MapConfigFactory.fromMap(Map.of("grpcServer", Map.of("port", 8090)));
    }

    default ConfigValueExtractor<GrpcServerConfig> grpcServerConfigExtractor() {
        return new $GrpcServerConfig_ConfigValueExtractor();
    }

    default AtomicReference<String> resRef() {
        return new AtomicReference<>("res1");
    }

    default String resNode(ValueOf<AtomicReference<String>> ref) {
        return ref.get().get();
    }

    default EventService eventService(String res) {
        return new EventService(res);
    }
}
