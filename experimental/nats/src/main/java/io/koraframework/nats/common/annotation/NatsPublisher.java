package io.koraframework.nats.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NatsPublisher {
    /**
     * Publisher configuration path, including driverProperties, JetStream options and telemetry.
     */
    String value();

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @interface Subject {
        /**
         * SubjectConfig path. A leading dot resolves relative to the publisher configuration.
         */
        String value();
    }

    /**
     * Request/reply method; response is deserialized with NatsDeserializer<T>.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @interface Request {
    }
}
