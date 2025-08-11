package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import io.micrometer.core.instrument.Tag;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Properties;

public interface MicrometerKafkaConsumerTagsProvider {

    List<Tag> getRecordDurationTags(@Nullable String clientId,
                                    @Nullable String groupId,
                                    Properties driverProperties,
                                    RecordDurationKey key);

    List<Tag> getRecordsDurationTags(@Nullable String clientId,
                                     @Nullable String groupId,
                                     Properties driverProperties,
                                     RecordsDurationKey key);

    List<Tag> getTopicLagTags(@Nullable String clientId,
                              Properties driverProperties,
                              TopicLagKey key);
}
