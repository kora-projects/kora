package ru.tinkoff.kora.kafka.common.annotation;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method level annotation used to specify which topic method should be subscribed to by Kafka Consumer.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaListener {

    /**
     * @return config path
     * @see KafkaListenerConfig
     */
    String value();

    /**
     * @return <b>Русский</b>: Теги {@link Tag} для собственной разметки конкретного потребителя а не сгенерированного
     * <hr>
     * <b>English</b>: Tags {@link Tag} for marking kafka listener with custom tag and not generated
     */
    Class<?>[] tag() default {};
}
