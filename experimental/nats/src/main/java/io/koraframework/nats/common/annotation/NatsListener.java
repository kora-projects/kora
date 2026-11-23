package io.koraframework.nats.common.annotation;

import io.koraframework.common.annotation.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NatsListener {
    /**
     * Listener configuration path, including driverProperties, subscription options and telemetry.
     */
    String value();

    /**
     * Tag for this listener's configuration, connection, telemetry and container.
     */
    Class<?> tag() default Tag.class;
}
