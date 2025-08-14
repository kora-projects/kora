package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.*;

public class Opentelemetry120KafkaConsumerTagsProvider implements MicrometerKafkaConsumerTagsProvider {

    protected static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_NAME = stringKey("messaging.kafka.consumer.name");

    @Override
    public List<Tag> getRecordDurationTags(@Nullable String clientId,
                                           @Nullable String groupId,
                                           Properties driverProperties,
                                           RecordDurationKey key) {
        var partitionString = Integer.toString(key.partition());

        var tags = new ArrayList<Tag>(6);
        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        tags.add(Tag.of(MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME.getKey(), key.consumerName()));
        tags.add(Tag.of(MESSAGING_DESTINATION_NAME.getKey(), key.topic()));
        tags.add(Tag.of(MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), partitionString));
        tags.add(Tag.of(MESSAGING_DESTINATION_PARTITION_ID.getKey(), partitionString));
        tags.add(Tag.of(MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString()));
        tags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_GROUP.getKey(), Objects.requireNonNullElse(groupId, "").toString()));
        return tags;
    }

    @Override
    public List<Tag> getRecordsDurationTags(@Nullable String clientId,
                                            @Nullable String groupId,
                                            Properties driverProperties,
                                            RecordsDurationKey key) {
        var tags = new ArrayList<Tag>(6);
        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        tags.add(Tag.of(MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME.getKey(), key.consumerName()));
        tags.add(Tag.of(MESSAGING_OPERATION.getKey(), key.consumerName()));
        tags.add(Tag.of(MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString()));
        tags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_GROUP.getKey(), Objects.requireNonNullElse(groupId, "").toString()));
        return tags;
    }

    @Override
    public List<Tag> getTopicLagTags(@Nullable String clientId,
                                     Properties driverProperties,
                                     TopicLagKey key) {
        var tags = new ArrayList<Tag>(6);

        tags.add(Tag.of(MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME.getKey(), key.consumerName()));
        tags.add(Tag.of(MESSAGING_DESTINATION_NAME.getKey(), key.topic()));
        tags.add(Tag.of(MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), Objects.toString(key.partition())));
        tags.add(Tag.of(MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString()));

        return tags;
    }
}
