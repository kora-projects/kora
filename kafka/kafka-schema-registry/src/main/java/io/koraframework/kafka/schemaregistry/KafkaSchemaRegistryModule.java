package io.koraframework.kafka.schemaregistry;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public interface KafkaSchemaRegistryModule {

    default KafkaSchemaRegistryConfig kafkaSchemaRegistryConfig(Config config, ConfigValueMapper<KafkaSchemaRegistryConfig> mapper) {
        return mapper.mapOrThrow(config.get("kafka.schemaRegistry"));
    }

    @DefaultComponent
    default SchemaRegistryClient kafkaSchemaRegistryClient(KafkaSchemaRegistryConfig config) {
        return new CachedSchemaRegistryClient(config.urls(), config.identityMapCapacity(), config.properties());
    }
}
