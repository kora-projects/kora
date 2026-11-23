package io.koraframework.kafka.schemaregistry;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

import java.util.HashMap;
import java.util.Map;

public interface KafkaSchemaRegistryModule {

    default KafkaSchemaRegistryConfig kafkaSchemaRegistryConfig(Config config, ConfigValueMapper<KafkaSchemaRegistryConfig> mapper) {
        return mapper.mapOrThrow(config.get("kafka.schemaRegistry"));
    }

    @DefaultComponent
    default SchemaRegistryClient kafkaSchemaRegistryClient(KafkaSchemaRegistryConfig config) {
        var properties = new HashMap<>(config.properties());

        var basicAuth = config.basicAuth();
        if (basicAuth != null) {
            properties.put("basic.auth.credentials.source", "USER_INFO");
            properties.put("basic.auth.user.info", basicAuth.username() + ":" + basicAuth.password());
        }

        var bearerAuth = config.bearerAuth();
        if (bearerAuth != null) {
            properties.put("bearer.auth.credentials.source", "STATIC_TOKEN");
            properties.put("bearer.auth.token", bearerAuth.token());
        }

        return new CachedSchemaRegistryClient(config.urls(), config.identityMapCapacity(), Map.copyOf(properties));
    }
}
