package io.koraframework.kafka.schemaregistry;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.List;
import java.util.Map;

@ConfigMapper
public interface KafkaSchemaRegistryConfig {

    List<String> urls();

    default int identityMapCapacity() {
        return 1000;
    }

    default Map<String, Object> properties() {
        return Map.of();
    }
}
