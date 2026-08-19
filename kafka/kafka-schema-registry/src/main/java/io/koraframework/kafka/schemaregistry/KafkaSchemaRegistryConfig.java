package io.koraframework.kafka.schemaregistry;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@ConfigMapper
public interface KafkaSchemaRegistryConfig {

    List<String> urls();

    default int identityMapCapacity() {
        return 1000;
    }

    /**
     * <b>Русский</b>: Базовая аутентификация (логин/пароль) для доступа к реестру схем.
     * <hr>
     * <b>English</b>: Basic authentication (username/password) for the Schema Registry.
     */
    @Nullable
    BasicAuth basicAuth();

    /**
     * <b>Русский</b>: Аутентификация по токену (Bearer) для доступа к реестру схем.
     * <hr>
     * <b>English</b>: Bearer token authentication for the Schema Registry.
     */
    @Nullable
    BearerAuth bearerAuth();

    /**
     * <b>Русский</b>: Произвольные дополнительные свойства клиента реестра (например, SSL/TLS).
     * <hr>
     * <b>English</b>: Additional raw registry client properties (e.g. SSL/TLS).
     */
    default Map<String, Object> properties() {
        return Map.of();
    }

    @ConfigMapper
    interface BasicAuth {

        String username();

        String password();
    }

    @ConfigMapper
    interface BearerAuth {

        String token();
    }
}
