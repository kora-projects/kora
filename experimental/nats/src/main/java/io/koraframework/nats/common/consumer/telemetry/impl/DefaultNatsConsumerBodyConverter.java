package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.logging.common.masking.raw.DataMasker;
import io.koraframework.logging.common.masking.raw.JsonDataMasker;
import io.koraframework.nats.common.consumer.deserializer.JsonNatsDeserializer;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.nats.client.Message;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts raw consumer payloads for logging, selecting format-specific masking like Kafka.
 */
public class DefaultNatsConsumerBodyConverter {
    private final Map<String, DataMasker> dataMaskers = new HashMap<>();

    public DefaultNatsConsumerBodyConverter() {
        this(List.of());
    }

    public DefaultNatsConsumerBodyConverter(List<DataMasker> dataMaskers) {
        for (var masker : dataMaskers) {
            this.dataMaskers.put(masker.format(), masker);
        }
    }

    public @Nullable String convert(Message message, NatsDeserializer<?> deserializer) {
        return convertBody(message.getData(), selectDataMasker(deserializer));
    }

    protected @Nullable DataMasker selectDataMasker(NatsDeserializer<?> deserializer) {
        return deserializer instanceof JsonNatsDeserializer<?> ? dataMaskers.get(JsonDataMasker.FORMAT) : null;
    }

    protected @Nullable String convertBody(byte @Nullable [] value, @Nullable DataMasker masker) {
        return value == null ? null : masker == null ? new String(value, StandardCharsets.UTF_8) : masker.mask(value);
    }
}
