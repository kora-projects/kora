package ru.tinkoff.kora.grpc.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.grpc.GrpcModule;
import ru.tinkoff.kora.grpc.config.GrpcServerConfig;

import java.util.concurrent.atomic.AtomicReference;

@KoraApp
public interface Application extends GrpcModule {
    default Config config() {
        return ConfigFactory.parseString("grpcServer.port = 8090");
    }

    default ConfigValueExtractor<GrpcServerConfig> grpcServerConfigExtractor() {
        return new ObjectConfigValueExtractor<GrpcServerConfig>() {
            @Override
            protected GrpcServerConfig extract(Config config) {
                return new GrpcServerConfig(config.getInt("port"));
            }
        };
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
