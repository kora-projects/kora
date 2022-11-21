package ru.tinkoff.kora.config.annotation.processor.cases;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.ConfigSource;

import java.util.Map;

@KoraApp
public interface AppWithConfigSource {
    @ConfigSource("some.config")
    record SomeConfig(String field1, int field2) {}

    default LifecycleWrapper<Config> config() {
        var config = ConfigFactory.parseMap(Map.of(
            "some", Map.of(
                "config", Map.of(
                    "field1", "field",
                    "field2", 42
                )
            )
        ));
        return new LifecycleWrapper<>(config, v -> Mono.empty(), v -> Mono.empty());
    }

    default MockLifecycle component(SomeConfig someConfig) {
        return new MockLifecycle() {};
    }
}
