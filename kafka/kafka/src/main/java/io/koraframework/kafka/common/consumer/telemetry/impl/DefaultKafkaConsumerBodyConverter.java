package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.consumer.containers.ConsumerRecordWrapper;
import io.koraframework.kafka.common.consumer.deserializer.JsonKafkaDeserializer;
import io.koraframework.logging.common.masking.raw.DataMasker;
import io.koraframework.logging.common.masking.raw.JsonDataMasker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultKafkaConsumerBodyConverter {

    private final Map<String, DataMasker> dataMaskers;

    public DefaultKafkaConsumerBodyConverter() {
        this(List.of());
    }

    public DefaultKafkaConsumerBodyConverter(List<DataMasker> dataMaskers) {
        this.dataMaskers = new HashMap<>();
        for (var dataMasker : dataMaskers) {
            this.dataMaskers.put(dataMasker.format(), dataMasker);
        }
    }

    @Nullable
    public String convertKey(ConsumerRecord<?, ?> record) {
        if (record instanceof ConsumerRecordWrapper<?, ?> wrapper) {
            return convertBody(wrapper.unwrap().key(), this.selectKeyDataMasker(record));
        }
        return null;
    }

    @Nullable
    public String convertValue(ConsumerRecord<?, ?> record) {
        if (record instanceof ConsumerRecordWrapper<?, ?> wrapper) {
            return convertBody(wrapper.unwrap().value(), this.selectValueDataMasker(record));
        }
        return null;
    }

    @Nullable
    protected DataMasker selectKeyDataMasker(ConsumerRecord<?, ?> record) {
        if (record instanceof ConsumerRecordWrapper<?, ?> wrapper
            && wrapper.keyDeserializer() instanceof JsonKafkaDeserializer<?>) {
            return this.dataMaskers.get(JsonDataMasker.FORMAT);
        }
        return null;
    }

    @Nullable
    protected DataMasker selectValueDataMasker(ConsumerRecord<?, ?> record) {
        if (record instanceof ConsumerRecordWrapper<?, ?> wrapper
            && wrapper.valueDeserializer() instanceof JsonKafkaDeserializer<?>) {
            return this.dataMaskers.get(JsonDataMasker.FORMAT);
        }
        return null;
    }

    @Nullable
    protected String convertBody(byte @Nullable [] value, @Nullable DataMasker dataMasker) {
        if (value == null) {
            return null;
        }
        return dataMasker == null ? new String(value, StandardCharsets.UTF_8) : dataMasker.mask(value);
    }
}
