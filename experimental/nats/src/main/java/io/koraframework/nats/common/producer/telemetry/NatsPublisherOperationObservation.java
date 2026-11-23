package io.koraframework.nats.common.producer.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.jspecify.annotations.Nullable;

/**
 * A declarative publisher operation, including atomic batch operations.
 */
public interface NatsPublisherOperationObservation extends Observation {
    default void observeResult(@Nullable Object result) {
    }

    default void end(@Nullable Throwable error) {
        if (error != null) {
            observeError(error);
        }
        end();
    }
}
