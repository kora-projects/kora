package ru.tinkoff.kora.kafka.common.annotation;

import ru.tinkoff.kora.kafka.common.producer.PublisherConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaPublisher {
    /**
     * @return config path
     * @see PublisherConfig
     */
    String value();
}
