package io.koraframework.kafka.common.annotation;

import io.koraframework.kafka.common.producer.KafkaPublisherConfig;
import io.koraframework.kafka.common.producer.KafkaPublisherConfig.TopicConfig;
import io.koraframework.kafka.common.producer.KafkaPublisherConfig.TransactionConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaPublisher {

    /**
     * @return path to config
     * @see KafkaPublisherConfig
     * @see TransactionConfig
     */
    String value();

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @interface Topic {

        /**
         * @return path to config path
         * @see TopicConfig
         */
        String value();
    }
}
