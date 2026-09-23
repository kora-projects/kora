package io.koraframework.kafka.common;

import io.koraframework.kafka.common.consumer.KafkaListenerModule;
import io.koraframework.kafka.common.producer.KafkaPublisherModule;

public interface KafkaModule extends KafkaListenerModule, KafkaPublisherModule {

}
