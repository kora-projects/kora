package io.koraframework.kafka.common.utils;

@FunctionalInterface
public interface KafkaArgMaskingStrategy {

    String mask(String key, Object value);
}
