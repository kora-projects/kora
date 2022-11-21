package ru.tinkoff.kora.kafka.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method level annotation used to specify which topic method should be subscribed to by Kafka Consumer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface KafkaIncoming {

    /**
     * @return topic name to subscribe to
     */
    String value();
}
