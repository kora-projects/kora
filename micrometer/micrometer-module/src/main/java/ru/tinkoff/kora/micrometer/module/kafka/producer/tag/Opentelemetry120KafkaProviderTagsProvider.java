package ru.tinkoff.kora.micrometer.module.kafka.producer.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.*;

public class Opentelemetry120KafkaProviderTagsProvider implements MicrometerKafkaProducerTagsProvider {

    @Override
    public List<Tag> getTopicPartitionTags(@Nullable String clientId,
                                           Properties driverProperties,
                                           RecordDurationKey key) {
        var tags = new ArrayList<Tag>(6);
        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        if (key.partition() == -1) {
            tags.add(Tag.of(MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), ""));
            tags.add(Tag.of(MESSAGING_DESTINATION_PARTITION_ID.getKey(), ""));
        } else {
            var partitionString = Integer.toString(key.partition());
            tags.add(Tag.of(MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), partitionString));
            tags.add(Tag.of(MESSAGING_DESTINATION_PARTITION_ID.getKey(), partitionString));
        }

        tags.add(Tag.of(MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MESSAGING_DESTINATION_NAME.getKey(), key.topic()));
        tags.add(Tag.of(MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString()));

        return tags;
    }
}
