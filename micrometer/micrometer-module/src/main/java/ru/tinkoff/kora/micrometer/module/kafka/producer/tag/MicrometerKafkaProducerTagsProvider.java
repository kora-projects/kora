package ru.tinkoff.kora.micrometer.module.kafka.producer.tag;

import io.micrometer.core.instrument.Tag;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Properties;

public interface MicrometerKafkaProducerTagsProvider {

    List<Tag> getTopicPartitionTags(@Nullable String clientId, Properties driverProperties, RecordDurationKey key);
}
