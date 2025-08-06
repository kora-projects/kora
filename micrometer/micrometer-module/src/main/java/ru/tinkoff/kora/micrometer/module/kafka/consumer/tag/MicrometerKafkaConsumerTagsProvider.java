package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import io.micrometer.core.instrument.Tag;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Properties;

public interface MicrometerKafkaConsumerTagsProvider {

    List<Tag> getDurationTags(@Nullable String clientId,
                              @Nullable String groupId,
                              Properties driverProperties,
                              DurationKey key);

    List<Tag> getDurationBatchTags(@Nullable String clientId,
                                   @Nullable String groupId,
                                   Properties driverProperties,
                                   DurationBatchKey key);

    List<Tag> getLagTags(@Nullable String clientId,
                         Properties driverProperties,
                         LagKey key);
}
